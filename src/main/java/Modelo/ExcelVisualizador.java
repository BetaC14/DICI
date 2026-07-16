package Modelo;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font; // Evita conflictos con java.awt.Font
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class ExcelVisualizador {
    
    private Workbook workbook;
    private Sheet hoja;
    private int primeraFila;
    private int ultimaFila;
    private int numColumnas;
    
    /**
     * CARGA UN ARCHIVO EXCEL Y DEVUELVE UN JTABLE CON FORMATO
     */
    public JTable cargarExcel(File archivo) throws Exception {
        try (FileInputStream fis = new FileInputStream(archivo)) {
            if (archivo.getName().toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (archivo.getName().toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new Exception("Formato de archivo no soportado");
            }
            
            hoja = encontrarHojaConDatos(workbook);
            if (hoja == null) {
                throw new Exception("No se encontraron datos en el Excel");
            }
            
            primeraFila = encontrarPrimeraFilaConDatos(hoja);
            ultimaFila = hoja.getLastRowNum();
            
            Row filaEncabezados = hoja.getRow(primeraFila);
            numColumnas = filaEncabezados.getLastCellNum();
            
            DefaultTableModel modelo = new DefaultTableModel();
            
            for (int i = 0; i < numColumnas; i++) {
                Cell celda = filaEncabezados.getCell(i);
                String nombre = (celda != null) ? getCellValueAsString(celda) : "Columna " + (i + 1);
                modelo.addColumn(nombre);
            }
            
            for (int i = primeraFila + 1; i <= ultimaFila; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;
                
                Object[] filaData = new Object[numColumnas];
                boolean filaVacia = true;
                
                for (int j = 0; j < numColumnas; j++) {
                    Cell celda = fila.getCell(j);
                    String valor = (celda != null) ? getCellValueAsString(celda) : "";
                    filaData[j] = valor;
                    if (!valor.trim().isEmpty()) {
                        filaVacia = false;
                    }
                }
                
                if (!filaVacia) {
                    modelo.addRow(filaData);
                }
            }
            
            JTable tabla = new JTable(modelo);
            aplicarEstilos(tabla);
            
            tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            tabla.setRowHeight(22);
            tabla.setShowGrid(true);
            tabla.setGridColor(Color.LIGHT_GRAY);
            tabla.setCellSelectionEnabled(true);
            tabla.setSelectionBackground(new Color(200, 220, 255));
            
            return tabla;
        }
    }
    
    private Sheet encontrarHojaConDatos(Workbook workbook) {
        int numeroHojas = workbook.getNumberOfSheets();
        
        for (int i = 0; i < numeroHojas; i++) {
            Sheet hoja = workbook.getSheetAt(i);
            Row fila = hoja.getRow(hoja.getFirstRowNum());
            if (fila != null) {
                int celdasConDatos = 0;
                for (int j = 0; j < fila.getLastCellNum(); j++) {
                    Cell celda = fila.getCell(j);
                    if (celda != null) {
                        String valor = getCellValueAsString(celda);
                        if (!valor.trim().isEmpty()) {
                            celdasConDatos++;
                        }
                    }
                }
                if (celdasConDatos >= 3) {
                    return hoja;
                }
            }
        }
        return workbook.getSheetAt(0);
    }
    
    private int encontrarPrimeraFilaConDatos(Sheet hoja) {
        for (int i = hoja.getFirstRowNum(); i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null) continue;
            
            int celdasConDatos = 0;
            for (int j = 0; j < fila.getLastCellNum(); j++) {
                Cell celda = fila.getCell(j);
                if (celda != null) {
                    String valor = getCellValueAsString(celda);
                    if (!valor.trim().isEmpty()) {
                        celdasConDatos++;
                    }
                }
            }
            
            if (celdasConDatos >= 3) {
                return i;
            }
        }
        return hoja.getFirstRowNum();
    }
    
    private String getCellValueAsString(Cell celda) {
        if (celda == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(celda);
    }
    
    private void aplicarEstilos(JTable tabla) {
        TableCellRenderer renderer = new ExcelCellRenderer(hoja, primeraFila);
        tabla.setDefaultRenderer(Object.class, renderer);
        
        for (int i = 0; i < numColumnas && i < tabla.getColumnCount(); i++) {
            int anchoMaximo = 80;
            for (int j = 0; j < tabla.getRowCount(); j++) {
                Object valor = tabla.getValueAt(j, i);
                if (valor != null) {
                    int ancho = valor.toString().length() * 9;
                    if (ancho > anchoMaximo) {
                        anchoMaximo = ancho;
                    }
                }
            }
            anchoMaximo = Math.min(anchoMaximo + 30, 400);
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchoMaximo);
        }
    }
}

// ============================================================
// RENDERER PERSONALIZADO CORREGIDO
// ============================================================
class ExcelCellRenderer extends DefaultTableCellRenderer {
    private Sheet hoja;
    private int primeraFila;
    
    public ExcelCellRenderer(Sheet hoja, int primeraFila) {
        this.hoja = hoja;
        this.primeraFila = primeraFila;
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        
        // ESTILOS POR DEFECTO
        label.setBackground(Color.WHITE);
        label.setForeground(Color.BLACK);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12));
        label.setBorder(null);
        
        // OBTENER CELDA DEL EXCEL
        int filaExcel = primeraFila + 1 + row;
        Row fila = hoja.getRow(filaExcel);
        if (fila != null) {
            Cell celda = fila.getCell(column);
            if (celda != null) {
                aplicarEstiloCelda(label, celda);
            }
        }
        
        // SELECCIÓN DE SWING
        if (isSelected) {
            label.setBackground(new Color(200, 220, 255));
        }
        
        return label;
    }
    
    private void aplicarEstiloCelda(JLabel label, Cell celda) {
        CellStyle estilo = celda.getCellStyle();
        if (estilo == null) return;
        
        try {
            // 1. COLOR DE FONDO
            Color colorFondo = obtenerColorFondo(estilo);
            if (colorFondo != null) {
                label.setBackground(colorFondo);
            }
            
            // 2. FUENTE Y COLOR DE TEXTO
            Workbook wb = hoja.getWorkbook();
            org.apache.poi.ss.usermodel.Font fuentePOI = wb.getFontAt(estilo.getFontIndexAsInt());
            if (fuentePOI != null) {
                // Configurar la fuente visual
                int estiloFuente = java.awt.Font.PLAIN;
                if (fuentePOI.getBold()) estiloFuente |= java.awt.Font.BOLD;
                if (fuentePOI.getItalic()) estiloFuente |= java.awt.Font.ITALIC;
                
                int tamanio = fuentePOI.getFontHeightInPoints();
                if (tamanio < 8) tamanio = 12;
                
                label.setFont(new java.awt.Font(fuentePOI.getFontName(), estiloFuente, tamanio));
                
                // Extraer color del texto a través de la fuente
                Color colorTexto = obtenerColorTextoDesdeFuente(fuentePOI);
                if (colorTexto != null) {
                    label.setForeground(colorTexto);
                }
            }
            
            // 3. ALINEACIÓN (CORREGIDO USANDO ENUMS DE POI)
            HorizontalAlignment alineacion = estilo.getAlignment();
            if (alineacion == HorizontalAlignment.CENTER) {
                label.setHorizontalAlignment(SwingConstants.CENTER);
            } else if (alineacion == HorizontalAlignment.RIGHT) {
                label.setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                label.setHorizontalAlignment(SwingConstants.LEFT);
            }
            
        } catch (Exception e) {
            // Protección por si algún formato de celda es extraño
        }
    }
    
    private Color obtenerColorFondo(CellStyle estilo) {
        try {
            if (estilo.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
                if (estilo instanceof XSSFCellStyle) {
                    XSSFCellStyle xssfStyle = (XSSFCellStyle) estilo;
                    XSSFColor color = xssfStyle.getFillForegroundColorColor();
                    if (color != null) {
                        byte[] rgb = color.getRGB();
                        if (rgb != null && rgb.length >= 3) {
                            return new Color(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }
    
    private Color obtenerColorTextoDesdeFuente(org.apache.poi.ss.usermodel.Font fuentePOI) {
        try {
            if (fuentePOI instanceof org.apache.poi.xssf.usermodel.XSSFFont) {
                org.apache.poi.xssf.usermodel.XSSFFont xssFFont = (org.apache.poi.xssf.usermodel.XSSFFont) fuentePOI;
                XSSFColor color = xssFFont.getXSSFColor();
                if (color != null) {
                    byte[] rgb = color.getRGB();
                    if (rgb != null && rgb.length >= 3) {
                        return new Color(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }
    
}