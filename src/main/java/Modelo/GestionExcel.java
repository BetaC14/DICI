package Modelo;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.DateUtil;
import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.*;
import javax.swing.table.DefaultTableModel;
import java.util.Arrays;  // ← Agregar este import
/**
 * CLASE MEJORADA PARA GESTIÓN DE EXCEL CON FORMATOS COMPLEJOS
 * @author Ana
 */
public class GestionExcel {

    // ==================== CLASE INTERNA PARA ESTRUCTURA DE COLUMNA ====================
    static class ColumnaEstructura {
        int indice;
        String nombre;
        int ancho;
        String tipoDato;
    }

    // ==================== MÉTODO PRINCIPAL DE IMPORTACIÓN ====================
   // ==================== MÉTODO PRINCIPAL DE IMPORTACIÓN (MEJORADO PARA MÚLTIPLES HOJAS) ====================
public static void importarExcelAEAV(Connection con, File archivo, int idPlantilla, int idDocumento) {
    try {
        con.setAutoCommit(false);

        // 1. Limpiar celdas viejas de este documento
        String sqlLimpiar = "DELETE FROM valores_celda WHERE id_documento = ?";
        try (PreparedStatement psLimpiar = con.prepareStatement(sqlLimpiar)) {
            psLimpiar.setInt(1, idDocumento);
            psLimpiar.executeUpdate();
            System.out.println("🧹 Datos anteriores eliminados del documento ID: " + idDocumento);
        }

        // 2. Abrir y procesar el archivo Excel
        try (FileInputStream fis = new FileInputStream(archivo);
             Workbook workbook = WorkbookFactory.create(fis)) {

            // 🔥 BUSCAR LA HOJA QUE CONTIENE DATOS (NO LA PRIMERA NECESARIAMENTE)
            Sheet hoja = encontrarHojaConDatos(workbook);
            
            if (hoja == null) {
                throw new Exception("No se encontró ninguna hoja con datos válidos en el Excel.");
            }
            
            System.out.println("📊 Procesando hoja: " + hoja.getSheetName());

            // OBTENER FILA DE ENCABEZADOS (BUSCAR LA PRIMERA FILA CON CONTENIDO)
            Row filaEncabezado = encontrarFilaEncabezado(hoja);
            if (filaEncabezado == null) {
                throw new Exception("No se encontró una fila de encabezados válida en la hoja '" + hoja.getSheetName() + "'.");
            }

            // DETECTAR COLUMNAS
            List<ColumnaEstructura> columnas = detectarEstructuraColumnas(hoja, filaEncabezado);
            
            if (columnas.isEmpty()) {
                throw new Exception("No se detectaron columnas válidas en la hoja '" + hoja.getSheetName() + "'.");
            }

            // REGISTRAR COLUMNAS EN BD
            Map<String, Integer> mapaCampos = registrarColumnasEnBD(con, idPlantilla, columnas);

            // IMPORTAR DATOS
            importarDatosExcel(con, hoja, idDocumento, columnas, mapaCampos);

            con.commit();
            System.out.println("✅ Sincronización DICI Completada exitosamente desde la hoja: " + hoja.getSheetName());

        }
    } catch (Exception e) {
        try { 
            if (con != null) con.rollback(); 
        } catch (SQLException ex) { 
            ex.printStackTrace(); 
        }
        System.err.println("❌ Error en la importación: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Error al importar: " + e.getMessage(), e);
    } finally {
        try { 
            if (con != null) con.setAutoCommit(true); 
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
    }
}

private static Row encontrarFilaEncabezado(Sheet hoja) {
    int primerasFilas = Math.min(20, hoja.getLastRowNum() + 1);
    
    for (int i = hoja.getFirstRowNum(); i <= primerasFilas && i <= hoja.getLastRowNum(); i++) {
        Row fila = hoja.getRow(i);
        if (fila == null) continue;
        
        // CONTAR CELDAS CON CONTENIDO
        int celdasConContenido = 0;
        int ultimaCelda = fila.getLastCellNum();
        
        for (int j = 0; j < ultimaCelda && j < 20; j++) {
            Cell celda = fila.getCell(j);
            if (celda != null) {
                String valor = getCellValueAsString(celda);
                if (valor != null && !valor.trim().isEmpty()) {
                    celdasConContenido++;
                }
            }
        }
        
        // SI TIENE AL MENOS 3 CELDAS CON CONTENIDO, ES UN ENCABEZADO VÁLIDO
        if (celdasConContenido >= 3) {
            System.out.println("🔍 Encabezados encontrados en fila: " + i + " (con " + celdasConContenido + " columnas)");
            return fila;
        }
    }
    
    // FALLBACK: Usar la primera fila
    Row primeraFila = hoja.getRow(hoja.getFirstRowNum());
    if (primeraFila != null) {
        System.out.println("🔍 Usando primera fila como encabezado (fallback)");
    }
    return primeraFila;
} 
   // ==================== DETECTAR ESTRUCTURA DE COLUMNAS (CON FILTRO MEJORADO) ====================
private static List<ColumnaEstructura> detectarEstructuraColumnas(Sheet hoja, Row filaEncabezado) {
    List<ColumnaEstructura> columnas = new ArrayList<>();
    DataFormatter formatter = new DataFormatter();
    Set<Integer> columnasProcesadas = new HashSet<>();
    
    int ultimaCelda = filaEncabezado.getLastCellNum();
    
    // 🔥 PASO 1: ENCONTRAR LA ÚLTIMA COLUMNA CON DATOS REALES
    int ultimaColumnaConDatos = -1;
    for (int c = 0; c < ultimaCelda; c++) {
        Cell celda = filaEncabezado.getCell(c);
        if (celda != null) {
            String valor = getCellValueAsString(celda);
            // Solo considerar columnas con texto significativo
            if (valor != null && !valor.trim().isEmpty() && !valor.trim().matches("\\d+")) {
                ultimaColumnaConDatos = c;
            }
        }
    }
    
    // Si no hay columnas válidas, retornar vacío
    if (ultimaColumnaConDatos == -1) {
        System.out.println("⚠️ No se encontraron columnas con datos válidos");
        return columnas;
    }
    
    System.out.println("📌 Última columna con datos: " + ultimaColumnaConDatos);
    
    // 🔥 PASO 2: SOLO PROCESAR HASTA LA ÚLTIMA COLUMNA CON DATOS
    for (int c = 0; c <= ultimaColumnaConDatos; c++) {
        if (columnasProcesadas.contains(c)) {
            continue;
        }

        // Obtener nombre de columna
        String nombreColumna = obtenerNombreColumna(hoja, filaEncabezado, c, formatter);
        
        // 🔥 PASO 3: FILTRAR COLUMNAS VACÍAS
        if (nombreColumna == null || nombreColumna.trim().isEmpty()) {
            System.out.println("⚠️ Columna " + c + " vacía, saltando...");
            continue;
        }
        
        // 🔥 PASO 4: FILTRAR COLUMNAS QUE SOLO CONTIENEN NÚMEROS
        if (nombreColumna.matches("\\d+")) {
            System.out.println("⚠️ Columna " + c + " solo contiene números, saltando...");
            continue;
        }
        
        // 🔥 PASO 5: FILTRAR COLUMNAS QUE CONTIENEN SOLO SÍMBOLOS
        if (nombreColumna.matches("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]+")) {
            System.out.println("⚠️ Columna " + c + " solo contiene símbolos, saltando...");
            continue;
        }
        
        // 🔥 PASO 6: FILTRAR COLUMNAS CON PALABRAS MUY LARGAS (probablemente texto de título)
        if (nombreColumna.length() > 30) {
            System.out.println("⚠️ Columna " + c + " tiene texto muy largo, saltando...");
            continue;
        }

        // Limpiar nombre
        nombreColumna = limpiarNombreColumna(nombreColumna);

        // Detectar ancho de columna combinada
        int anchoColumna = 1;
        if (esCeldaCombinada(hoja, filaEncabezado.getRowNum(), c)) {
            anchoColumna = obtenerAnchoColumnaCombinada(hoja, filaEncabezado.getRowNum(), c);
            for (int i = c; i < c + anchoColumna && i <= ultimaColumnaConDatos; i++) {
                columnasProcesadas.add(i);
            }
        } else {
            columnasProcesadas.add(c);
        }

        // Crear estructura de columna
        ColumnaEstructura columna = new ColumnaEstructura();
        columna.indice = c;
        columna.nombre = nombreColumna;
        columna.ancho = anchoColumna;
        columna.tipoDato = "TEXTO";
        
        columnas.add(columna);
        System.out.println("📌 Columna válida: '" + nombreColumna + "' (índice:" + c + ")");
    }
    
    System.out.println("✅ Total columnas detectadas: " + columnas.size());
    return columnas;
}
    // ==================== OBTENER NOMBRE DE COLUMNA ====================
    private static String obtenerNombreColumna(Sheet hoja, Row filaEncabezado, 
                                              int columna, DataFormatter formatter) {
        if (esCeldaCombinada(hoja, filaEncabezado.getRowNum(), columna)) {
            return obtenerValorCeldaCombinada(hoja, filaEncabezado.getRowNum(), columna, formatter);
        }
        
        Cell celda = filaEncabezado.getCell(columna);
        return (celda != null) ? formatter.formatCellValue(celda).trim() : "";
    }

    // ==================== VERIFICAR CELDA COMBINADA ====================
    private static boolean esCeldaCombinada(Sheet hoja, int fila, int columna) {
        for (int i = 0; i < hoja.getNumMergedRegions(); i++) {
            CellRangeAddress region = hoja.getMergedRegion(i);
            if (region.isInRange(fila, columna)) {
                return true;
            }
        }
        return false;
    }

    // ==================== OBTENER ANCHO DE CELDA COMBINADA ====================
    private static int obtenerAnchoColumnaCombinada(Sheet hoja, int fila, int columna) {
        for (int i = 0; i < hoja.getNumMergedRegions(); i++) {
            CellRangeAddress region = hoja.getMergedRegion(i);
            if (region.isInRange(fila, columna)) {
                return region.getLastColumn() - region.getFirstColumn() + 1;
            }
        }
        return 1;
    }

    // ==================== OBTENER VALOR DE CELDA COMBINADA ====================
    private static String obtenerValorCeldaCombinada(Sheet hoja, int fila, int columna, 
                                                     DataFormatter formatter) {
        for (int i = 0; i < hoja.getNumMergedRegions(); i++) {
            CellRangeAddress region = hoja.getMergedRegion(i);
            if (region.isInRange(fila, columna)) {
                Row primeraFila = hoja.getRow(region.getFirstRow());
                if (primeraFila != null) {
                    Cell primeraCelda = primeraFila.getCell(region.getFirstColumn());
                    if (primeraCelda != null) {
                        return formatter.formatCellValue(primeraCelda).trim();
                    }
                }
                break;
            }
        }
        return "";
    }

    // ==================== LIMPIAR NOMBRE DE COLUMNA ====================
    private static String limpiarNombreColumna(String nombre) {
        nombre = nombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]", " ")
                       .replaceAll("\\s+", " ")
                       .trim();
        
        if (nombre.isEmpty()) {
            nombre = "columna_" + System.currentTimeMillis();
        }
        
        if (nombre.length() > 255) {
            nombre = nombre.substring(0, 255);
        }
        
        return nombre;
    }
    

/**
 * GUARDA EL ARCHIVO ORIGINAL EN LA BASE DE DATOS COMO BLOB
 */
public static void guardarArchivoOriginal(Connection con, File archivo, int idDocumento) {
    try {
        String sql = "UPDATE documentos_planilla SET archivo_original = ?, nombre_archivo_original = ? WHERE id_documento = ?";
        
        // Leer el archivo como bytes
        byte[] archivoBytes = java.nio.file.Files.readAllBytes(archivo.toPath());
        
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBytes(1, archivoBytes);
            ps.setString(2, archivo.getName());
            ps.setInt(3, idDocumento);
            int filasActualizadas = ps.executeUpdate();
            
            if (filasActualizadas > 0) {
                System.out.println("✅ Archivo original guardado en BD: " + archivo.getName() + 
                    " (" + archivoBytes.length + " bytes)");
            } else {
                System.out.println("⚠️ No se pudo guardar el archivo original. Documento ID: " + idDocumento);
            }
        }
        
    } catch (Exception e) {
        System.err.println("❌ Error al guardar archivo original: " + e.getMessage());
        e.printStackTrace();
    }
}
/**
 * OBTIENE EL VALOR DE UNA CELDA COMO STRING DE FORMA SEGURA
 * Este método maneja todos los tipos de celda y devuelve un String
 */
private static String getCellValueAsString(Cell celda) {
    if (celda == null) {
        return "";
    }
    
    DataFormatter formatter = new DataFormatter();
    
    try {
        switch (celda.getCellType()) {
            case STRING:
                return celda.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(celda)) {
                    try {
                        java.util.Date fecha = celda.getDateCellValue();
                        return new java.text.SimpleDateFormat("dd/MM/yyyy").format(fecha);
                    } catch (Exception e) {
                        return formatter.formatCellValue(celda);
                    }
                }
                double valor = celda.getNumericCellValue();
                if (valor == (long) valor) {
                    return String.valueOf((long) valor);
                }
                return String.valueOf(valor);
            case BOOLEAN:
                return String.valueOf(celda.getBooleanCellValue());
            case FORMULA:
                try {
                    return formatter.formatCellValue(celda);
                } catch (Exception e) {
                    return celda.getCellFormula();
                }
            case BLANK:
                return "";
            default:
                return formatter.formatCellValue(celda);
        }
    } catch (Exception e) {
        return formatter.formatCellValue(celda);
    }
}
    // ==================== REGISTRAR COLUMNAS EN BD ====================
    private static Map<String, Integer> registrarColumnasEnBD(Connection con, 
                                                              int idPlantilla, 
                                                              List<ColumnaEstructura> columnas) 
            throws SQLException {
        
        Map<String, Integer> mapaCampos = new HashMap<>();
        String sqlInsertar = "INSERT OR IGNORE INTO campos_plantilla (id_plantilla, nombre_columna) VALUES (?, ?)";
        String sqlObtenerId = "SELECT id_campo FROM campos_plantilla WHERE id_plantilla = ? AND nombre_columna = ?";
        
        for (ColumnaEstructura columna : columnas) {
            try (PreparedStatement psInsert = con.prepareStatement(sqlInsertar)) {
                psInsert.setInt(1, idPlantilla);
                psInsert.setString(2, columna.nombre);
                psInsert.executeUpdate();
            }
            
            try (PreparedStatement psGetId = con.prepareStatement(sqlObtenerId)) {
                psGetId.setInt(1, idPlantilla);
                psGetId.setString(2, columna.nombre);
                try (ResultSet rs = psGetId.executeQuery()) {
                    if (rs.next()) {
                        int idCampo = rs.getInt("id_campo");
                        mapaCampos.put(columna.nombre, idCampo);
                    }
                }
            }
        }
        
        return mapaCampos;
    }

    // ==================== IMPORTAR DATOS DEL EXCEL ====================
    private static void importarDatosExcel(Connection con, Sheet hoja, int idDocumento,List<ColumnaEstructura> columnas, Map<String, Integer> mapaCampos) 
            throws SQLException {
        
        String sqlInsertar = "INSERT INTO valores_celda (id_documento, id_campo, numero_fila, valor_texto) VALUES (?, ?, ?, ?)";
        DataFormatter formatter = new DataFormatter();
        int filaInicio = 1;
        int filaFin = hoja.getLastRowNum();
        int contadorCeldas = 0;

        try (PreparedStatement psValor = con.prepareStatement(sqlInsertar)) {
            for (int i = filaInicio; i <= filaFin; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                for (ColumnaEstructura columna : columnas) {
                    String valorTexto = obtenerValorCeldaConFormato(hoja, fila, i, columna.indice, formatter);
                    
                    if (valorTexto == null || valorTexto.trim().isEmpty()) {
                        continue;
                    }

                    Integer idCampo = mapaCampos.get(columna.nombre);
                    if (idCampo == null) {
                        continue;
                    }

                    psValor.setInt(1, idDocumento);
                    psValor.setInt(2, idCampo);
                    psValor.setInt(3, i);
                    psValor.setString(4, valorTexto.trim());
                    
                    psValor.addBatch();
                    contadorCeldas++;
                }
                
                if (contadorCeldas % 1000 == 0) {
                    psValor.executeBatch();
                }
            }
            
            psValor.executeBatch();
            System.out.println("✅ Importadas " + contadorCeldas + " celdas");
        }
    }

    // ==================== OBTENER VALOR DE CELDA CON FORMATO ====================
    private static String obtenerValorCeldaConFormato(Sheet hoja, Row fila, int numeroFila, 
                                                      int columna, DataFormatter formatter) {
        Cell celda = fila.getCell(columna);
        if (celda == null) return "";
        
        if (esCeldaCombinada(hoja, numeroFila, columna)) {
            return obtenerValorCeldaCombinada(hoja, numeroFila, columna, formatter);
        }
        
        return formatearValorCelda(celda, formatter);
    }

    // ==================== FORMATEAR VALOR DE CELDA ====================
    private static String formatearValorCelda(Cell celda, DataFormatter formatter) {
        try {
            switch (celda.getCellType()) {
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(celda)) {
                        try {
                            java.util.Date fecha = celda.getDateCellValue();
                            return new java.text.SimpleDateFormat("dd/MM/yyyy").format(fecha);
                        } catch (Exception e) {
                            return formatter.formatCellValue(celda);
                        }
                    }
                    double valor = celda.getNumericCellValue();
                    if (valor == (long) valor) {
                        return String.valueOf((long) valor);
                    }
                    return String.valueOf(valor);
                    
                case STRING:
                    return celda.getStringCellValue();
                    
                case BOOLEAN:
                    return String.valueOf(celda.getBooleanCellValue());
                    
                case FORMULA:
                    try {
                        return formatter.formatCellValue(celda);
                    } catch (Exception e) {
                        return celda.getCellFormula();
                    }
                    
                default:
                    return formatter.formatCellValue(celda);
            }
        } catch (Exception e) {
            return formatter.formatCellValue(celda);
        }
    }
/**
 * ENCUENTRA LA HOJA QUE CONTIENE DATOS VÁLIDOS
 * Busca en todas las hojas y devuelve la primera que tenga datos estructurados
 */
// ==================== ENCONTRAR HOJA CON DATOS (MEJORADO) ====================
private static Sheet encontrarHojaConDatos(Workbook workbook) {
    int numeroHojas = workbook.getNumberOfSheets();
    System.out.println("📋 Buscando datos en " + numeroHojas + " hojas...");
    
    // 🔥 LISTA DE NOMBRES DE HOJAS A IGNORAR (VACÍAS O SIN DATOS)
    List<String> hojasIgnorar = Arrays.asList(
        "MI HORARIO", 
        "DASHBOARD", 
        "RESUMEN", 
        "PORTADA", 
        "INICIO",
        "CONFIGURACION",
        "CONFIG"
    );
    
    // 🔥 PRIMERO: BUSCAR HOJAS CON DATOS ESTRUCTURADOS
    List<Sheet> hojasConDatos = new ArrayList<>();
    
    for (int i = 0; i < numeroHojas; i++) {
        Sheet hoja = workbook.getSheetAt(i);
        String nombreHoja = hoja.getSheetName();
        String nombreHojaUpper = nombreHoja.toUpperCase();
        
        // Saltar hojas ignoradas
        if (hojasIgnorar.contains(nombreHojaUpper)) {
            System.out.println("  ⏭️ Saltando hoja: '" + nombreHoja + "' (ignorada)");
            continue;
        }
        
        System.out.println("  🔍 Revisando hoja: '" + nombreHoja + "'");
        
        // Verificar si la hoja tiene datos
        Row filaEncabezado = encontrarFilaEncabezado(hoja);
        if (filaEncabezado != null) {
            // Contar columnas con contenido
            int celdasConContenido = 0;
            for (int j = 0; j < Math.min(filaEncabezado.getLastCellNum(), 20); j++) {
                Cell celda = filaEncabezado.getCell(j);
                if (celda != null) {
                    String valor = getCellValueAsString(celda);
                    if (valor != null && !valor.trim().isEmpty() && !valor.trim().matches("\\d+")) {
                        celdasConContenido++;
                    }
                }
            }
            
            // Si tiene al menos 3 columnas con texto significativo, es una hoja válida
            if (celdasConContenido >= 3) {
                System.out.println("  ✅ Hoja '" + nombreHoja + "' tiene " + celdasConContenido + " columnas con datos");
                hojasConDatos.add(hoja);
            } else {
                System.out.println("  ⚠️ Hoja '" + nombreHoja + "' tiene pocas columnas con datos (" + celdasConContenido + ")");
            }
        }
    }
    
    // Si encontramos hojas con datos
    if (!hojasConDatos.isEmpty()) {
        // Si solo una, devolverla
        if (hojasConDatos.size() == 1) {
            System.out.println("  ✅ Seleccionada hoja: '" + hojasConDatos.get(0).getSheetName() + "'");
            return hojasConDatos.get(0);
        }
        
        // Si varias, devolver la que tenga más columnas (probablemente la principal)
        Sheet mejorHoja = hojasConDatos.get(0);
        int maxColumnas = 0;
        for (Sheet hoja : hojasConDatos) {
            Row fila = hoja.getRow(hoja.getFirstRowNum());
            if (fila != null) {
                int columnas = 0;
                for (int j = 0; j < fila.getLastCellNum(); j++) {
                    Cell celda = fila.getCell(j);
                    if (celda != null) {
                        String valor = getCellValueAsString(celda);
                        if (valor != null && !valor.trim().isEmpty()) {
                            columnas++;
                        }
                    }
                }
                if (columnas > maxColumnas) {
                    maxColumnas = columnas;
                    mejorHoja = hoja;
                }
            }
        }
        System.out.println("  ✅ Seleccionada hoja: '" + mejorHoja.getSheetName() + "' con " + maxColumnas + " columnas");
        return mejorHoja;
    }
    
    // Si ninguna hoja tiene datos, usar la primera hoja como fallback
    System.out.println("  ⚠️ Ninguna hoja con datos válidos. Usando primera hoja como fallback.");
    return workbook.getSheetAt(0);
}
    // ==================== EXPORTAR A EXCEL (MANTENIDO) ====================
    public static void exportarEAVAExcel(Connection con, File archivoDestino, int idPlantilla, int idDocumento) {
        String ruta = archivoDestino.getAbsolutePath();
        if (!ruta.endsWith(".xlsx")) {
            ruta += ".xlsx";
        }

        String sqlEncabezados = "SELECT nombre_columna FROM campos_plantilla WHERE id_plantilla = ? ORDER BY id_campo ASC";
        String sqlDatos = """
            SELECT vc.numero_fila, cp.nombre_columna, vc.valor_texto 
            FROM valores_celda vc
            JOIN campos_plantilla cp ON vc.id_campo = cp.id_campo
            WHERE vc.id_documento = ?
            ORDER BY vc.numero_fila ASC, cp.id_campo ASC
        """;

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.FileOutputStream fileOut = new java.io.FileOutputStream(ruta)) {

            Sheet hoja = workbook.createSheet("Reporte DICI");

            // 1. Escribir Encabezados
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

            // 2. Poblar Datos
            try (PreparedStatement psData = con.prepareStatement(sqlDatos)) {
                psData.setInt(1, idDocumento);
                
                try (ResultSet rsData = psData.executeQuery()) {
                    Row filaActual = null;
                    int filaActualIdx = -1;

                    while (rsData.next()) {
                        int filaLogica = rsData.getInt("numero_fila");
                        String columna = rsData.getString("nombre_columna");
                        String valor = rsData.getString("valor_texto");

                        if (filaLogica != filaActualIdx) {
                            filaActualIdx = filaLogica;
                            filaActual = hoja.createRow(filaLogica); 
                        }

                        int colInsertar = columnas.indexOf(columna);
                        if (colInsertar != -1 && filaActual != null) {
                            filaActual.createCell(colInsertar).setCellValue(valor);
                        }
                    }
                }
            }

            workbook.write(fileOut);
            System.out.println("Archivo Excel exportado en: " + ruta);

        } catch (Exception e) {
            System.err.println("Error exportando: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== LISTAR DOCUMENTOS ====================
    public static DefaultTableModel obtenerListaDocumentos(Connection con, int idPlantilla, int idPeriodo) {
        DefaultTableModel modelo = new DefaultTableModel(new Object[]{"ID Documento", "Nombre del Formato", "Fecha de Registro"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        
        String sql = "SELECT id_documento, nombre_archivo_origen, fecha_registro " +
                     "FROM documentos_planilla WHERE id_plantilla = ? AND id_periodo = ? ORDER BY fecha_registro DESC;";
        
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, idPlantilla);
            pstmt.setInt(2, idPeriodo);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    modelo.addRow(new Object[]{
                        rs.getInt("id_documento"),
                        rs.getString("nombre_archivo_origen"),
                        rs.getString("fecha_registro")
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al listar documentos: " + e.getMessage());
        }
        return modelo;
    }

    // ==================== COMPROBAR DUPLICADOS ====================
    public static int obtenerIdDocumentoPorNombre(Connection con, String nombre, int idPlantilla, int idPeriodo) {
        String sql = "SELECT id_documento FROM documentos_planilla WHERE nombre_archivo_origen = ? AND id_plantilla = ? AND id_periodo = ?;";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setInt(2, idPlantilla);
            pstmt.setInt(3, idPeriodo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id_documento");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar nombre duplicado: " + e.getMessage());
        }
        return -1;
    }

// ==================== RECUPERAR ARCHIVO ORIGINAL DE LA BD (CORREGIDO) ====================
public static File recuperarArchivoOriginal(Connection con, int idDocumento) {
    // Usamos EXACTAMENTE las columnas de tu script de baseInicial
    String sql = "SELECT archivo_original, nombre_archivo_original FROM documentos_planilla WHERE id_documento = ?";
    try (PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setInt(1, idDocumento);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                byte[] bytes = rs.getBytes("archivo_original");
                String nombreOriginal = rs.getString("nombre_archivo_original");
                
                // Si el registro está vacío o es de un formato viejo sin respaldo
                if (bytes == null) {
                    System.out.println("⚠️ El documento ID " + idDocumento + " no tiene archivo binario en la BD.");
                    return null; 
                }
                
                // Fallback por si el nombre original guardado fuese nulo o vacío
                if (nombreOriginal == null || nombreOriginal.trim().isEmpty()) {
                    nombreOriginal = "documento_dici.xlsx";
                }
                
                // Creamos el archivo temporal en el sistema de manera segura
                File tempFile = File.createTempFile("dici_temp_", "_" + nombreOriginal);
                tempFile.deleteOnExit(); // Se elimina automáticamente al cerrar la aplicación
                
                // Escribimos los bytes recuperados en el archivo temporal
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    fos.write(bytes);
                }
                
                System.out.println("✅ Archivo original reconstruido con éxito en: " + tempFile.getAbsolutePath());
                return tempFile;
            }
        }
    } catch (Exception e) {
        System.err.println("❌ Error en GestionExcel al recuperar el archivo: " + e.getMessage());
        e.printStackTrace();
    }
    return null;
}
    // ==================== CARGAR DETALLE DE DOCUMENTO ====================
public static DefaultTableModel cargarDetalleDocumento(Connection con, int idDocumento) {
    // 🔥 CREAR UN MODELO NUEVO Y LIMPIO
    DefaultTableModel modelo = new DefaultTableModel();
    
    try {
        String sqlColumnas = "SELECT cp.id_campo, cp.nombre_columna FROM campos_plantilla cp " +
                             "JOIN documentos_planilla dp ON cp.id_plantilla = dp.id_plantilla " +
                             "WHERE dp.id_documento = ? ORDER BY cp.id_campo ASC;";
        
        ArrayList<Integer> idCampos = new ArrayList<>();
        try (PreparedStatement pstmtCol = con.prepareStatement(sqlColumnas)) {
            pstmtCol.setInt(1, idDocumento);
            try (ResultSet rsCol = pstmtCol.executeQuery()) {
                while (rsCol.next()) {
                    modelo.addColumn(rsCol.getString("nombre_columna"));
                    idCampos.add(rsCol.getInt("id_campo"));
                }
            }
        }

        if (idCampos.isEmpty()) {
            return modelo; 
        }

        String sqlValores = "SELECT numero_fila, id_campo, valor_texto FROM valores_celda " +
                            "WHERE id_documento = ? ORDER BY numero_fila ASC, id_campo ASC;";
        
        try (PreparedStatement pstmtVal = con.prepareStatement(sqlValores)) {
            pstmtVal.setInt(1, idDocumento);
            try (ResultSet rsVal = pstmtVal.executeQuery()) {
                
                int filaActualProcesada = -1;
                Vector<Object> filaDatos = null;

                while (rsVal.next()) {
                    int filaExcel = rsVal.getInt("numero_fila");
                    int idCampoCur = rsVal.getInt("id_campo");
                    String valor = rsVal.getString("valor_texto");

                    if (filaExcel != filaActualProcesada) {
                        if (filaDatos != null) {
                            modelo.addRow(filaDatos);
                        }
                        filaActualProcesada = filaExcel;
                        filaDatos = new Vector<>();
                        for (int i = 0; i < idCampos.size(); i++) {
                            filaDatos.add("");
                        }
                    }

                    int columnaIndice = idCampos.indexOf(idCampoCur);
                    if (columnaIndice != -1 && filaDatos != null) {
                        filaDatos.set(columnaIndice, valor);
                    }
                }

                if (filaDatos != null) {
                    modelo.addRow(filaDatos);
                }
            }
        }
        
    } catch (SQLException e) {
        System.err.println("Error al reconstruir el documento: " + e.getMessage());
        e.printStackTrace();
    }
    
    return modelo;
  }
}