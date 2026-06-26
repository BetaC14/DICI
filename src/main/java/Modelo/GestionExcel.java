/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Modelo;

/**
 * @author Ana
 */
import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.*;

public class GestionExcel {

    public static void importarExcelAEAV(Connection con, File archivo, int idPlantilla, int idPeriodo) {
        String nombreArchivo = archivo.getName();
        int idDocumento = -1;

        try {
            // Desactivar autocommit para controlar la transacción manualmente
            con.setAutoCommit(false);

            //  Asegurar la existencia del documento 
            String sqlDoc = "INSERT OR IGNORE INTO documentos_planilla (id_plantilla, id_periodo, nombre_archivo_origen) VALUES (?, ?, ?)";
            try (PreparedStatement psDoc = con.prepareStatement(sqlDoc)) {
                psDoc.setInt(1, idPlantilla);
                psDoc.setInt(2, idPeriodo);
                psDoc.setString(3, nombreArchivo);
                psDoc.executeUpdate();
            }

            // Recuperar el id_documento asignado o existente
            String sqlGetDoc = "SELECT id_documento FROM documentos_planilla WHERE id_plantilla = ? AND id_periodo = ?";
            try (PreparedStatement psGetDoc = con.prepareStatement(sqlGetDoc)) {
                psGetDoc.setInt(1, idPlantilla);
                psGetDoc.setInt(2, idPeriodo);
                try (ResultSet rs = psGetDoc.executeQuery()) {
                    if (rs.next()) {
                        idDocumento = rs.getInt("id_documento");
                    }
                }
            }

            // iMPORTANTEEEE Limpiar datos anteriores de este documento para mantener "solo una copia" limpia
            String sqlLimpiar = "DELETE FROM valores_celda WHERE id_documento = ?";
            try (PreparedStatement psLimpiar = con.prepareStatement(sqlLimpiar)) {
                psLimpiar.setInt(1, idDocumento);
                psLimpiar.executeUpdate();
            }

            //  Obtener los IDs de los campos ordenados para mapear el Excel de forma secuencial
            List<Integer> idCampos = new ArrayList<>();
            String sqlCampos = "SELECT id_campo FROM campos_plantilla WHERE id_plantilla = ? ORDER BY id_campo ASC";
            try (PreparedStatement psCampos = con.prepareStatement(sqlCampos)) {
                psCampos.setInt(1, idPlantilla);
                try (ResultSet rs = psCampos.executeQuery()) {
                    while (rs.next()) {
                        idCampos.add(rs.getInt("id_campo"));
                    }
                }
            }

            if (idCampos.isEmpty()) {
                throw new Exception("La plantilla seleccionada no tiene campos configurados.");
            }

            //  Leer el archivo Excel e insertar en valores_celda
            String sqlInsertarValor = "INSERT INTO valores_celda (id_documento, id_campo, numero_fila, valor_texto) VALUES (?, ?, ?, ?)";
            
            try (FileInputStream fis = new FileInputStream(archivo);
                 Workbook workbook = WorkbookFactory.create(fis);
                 PreparedStatement psValor = con.prepareStatement(sqlInsertarValor)) {

                Sheet hoja = workbook.getSheetAt(0);
                int filasTotales = hoja.getLastRowNum();
                DataFormatter formatter = new DataFormatter(); // Ayuda a convertir cualquier celda a texto plano

                // Iterar las filas 
                for (int i = 1; i <= filasTotales; i++) {
                    Row fila = hoja.getRow(i);
                    if (fila == null) continue;

                    // Mapear cada columna del Excel con su respectivo id_campo en la BD
                    for (int c = 0; c < idCampos.size(); c++) {
                        Cell celda = fila.getCell(c);
                        String valorTexto = (celda != null) ? formatter.formatCellValue(celda) : "";
                        
                        psValor.setInt(1, idDocumento);
                        psValor.setInt(2, idCampos.get(c));
                        psValor.setInt(3, i); 
                        psValor.setString(4, valorTexto);
                        
                        psValor.addBatch();
                    }
                }
                
                psValor.executeBatch(); // Guardado masivo ultrarrápido
            }

            con.commit(); // Confirmamos todos los cambios en baseInicial
            System.out.println("Importación finalizada con éxito. Datos actualizados.");

        } catch (Exception e) {
            try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.err.println("Error procesando la importación: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { con.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    public static void exportarEAVAExcel(Connection con, File archivoDestino, int idPlantilla, int idPeriodo) {
        String ruta = archivoDestino.getAbsolutePath();
        if (!ruta.endsWith(".xlsx")) {
            ruta += ".xlsx";
        }

        // Consulta para obtener los nombres de las columnas reales de la plantilla
        String sqlEncabezados = "SELECT nombre_columna FROM campos_plantilla WHERE id_plantilla = ? ORDER BY id_campo ASC";
        
        // Consulta para extraer la matriz de datos organizada por fila y columna
        String sqlDatos = """
            SELECT vc.numero_fila, cp.nombre_columna, vc.valor_texto 
            FROM valores_celda vc
            JOIN documentos_planilla dp ON vc.id_documento = dp.id_documento
            JOIN campos_plantilla cp ON vc.id_campo = cp.id_campo
            WHERE dp.id_plantilla = ? AND dp.id_periodo = ?
            ORDER BY vc.numero_fila ASC, cp.id_campo ASC
        """;

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.FileOutputStream fileOut = new java.io.FileOutputStream(ruta)) {

            Sheet hoja = workbook.createSheet("Reporte");

            // 1. Escribir Encabezados Dinámicos
            Row filaEncabezado = hoja.createRow(0);
            List<String> columnas = new ArrayList<>();
            try (PreparedStatement psEnc = con.prepareStatement(sqlEncabezados)) {
                psEnc.setInt(1, idPlantilla);
                try (ResultSet rsEnc = psEnc.executeQuery()) {
                    int colIdx = 0;
                    while (rsEnc.next()) {
                        String colNombre = rsEnc.getString("nombre_columna");
                        columnas.add(colNombre);
                        filaEncabezado.createCell(colIdx++).setCellValue(colNombre);
                    }
                }
            }

            // 2. Poblar Filas de Datos
            try (PreparedStatement psData = con.prepareStatement(sqlDatos)) {
                psData.setInt(1, idPlantilla);
                psData.setInt(2, idPeriodo);
                
                try (ResultSet rsData = psData.executeQuery()) {
                    Row filaActual = null;
                    int filaActualIdx = -1;

                    while (rsData.next()) {
                        int numFilaExcel = rsData.getRow(); // Número correlativo interno
                        int filaLogica = rsData.getInt("numero_fila");
                        String columna = rsData.getString("nombre_columna");
                        String valor = rsData.getString("valor_texto");

                        // Si cambia el número de fila en los registros, creamos una nueva fila en Excel
                        if (filaLogica != filaActualIdx) {
                            filaActualIdx = filaLogica;
                            filaActual = hoja.createRow(filaLogica); // Mantiene la posición original
                        }

                        int colInsertar = columnas.indexOf(columna);
                        if (colInsertar != -1 && filaActual != null) {
                            filaActual.createCell(colInsertar).setCellValue(valor);
                        }
                    }
                }
            }

            workbook.write(fileOut);
            System.out.println("Archivo excel generado de forma única en: " + ruta);

        } catch (Exception e) {
            System.err.println("Error exportando los datos de la plantilla: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

