/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Modelo;
// Modelo de importaciojes para el funcionamiento d la importacion y exportacion :(
/**
 *
 * @author Ana Beleño
 */
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.*;
        
public class ModeloExcel {
    Workbook book;
    
    public String Importar(File archivo, JTable tabla){
        String mensaje="Error en la Iportacion";
        DefaultTableModel modelo=new DefaultTableModel();
        tabla.setModel(modelo);
        
        try { 
    //Esto crea archivos con extenciones xls y xlsx
        book=WorkbookFactory.create(new FileInputStream (archivo));    
            Sheet hoja=book.getSheetAt(0);
            Iterator FilaIterator=hoja.rowIterator();
            int IndiceFila=-1;
            // sera verddero si existen filas poir recorer
            while (FilaIterator.hasNext()){
                // Aumento de filas por cada recorrido
                IndiceFila++;
                Row fila=(Row)FilaIterator.next();
                //Recorre columnas o celdas de una fila ya ceada
                Iterator ColumnaIterator=fila.cellIterator();
                //Asigne un maximo de columnas permitidas
                Object []ListaColumna=new Object[99999];
                int IndiceColumna=-1;
                while (ColumnaIterator.hasNext()){
                    IndiceColumna++;
                    Cell celda=(Cell)ColumnaIterator.next();
                    if (IndiceFila==0){
                        modelo.addColumn(celda.getStringCellValue());
                        
                    }else{
                        if(celda!=null){
                            switch (celda.getCellType()){
                                case CellType.NUMERIC:
                                    ListaColumna[IndiceColumna]=(int)Math.round(celda.getNumericCellValue());
                                    break;
                                case CellType.STRING:
                                    ListaColumna[IndiceColumna]=celda.getStringCellValue();
                                    break;
                                case CellType.BOOLEAN:
                                    break;
                                default:
                                    ListaColumna[IndiceColumna]=celda.getDateCellValue();
                                    break;
                            }
                        }
                    }
                }
              if (IndiceFila!=0)modelo.addRow(ListaColumna);  
            }
            mensaje="Importacion Exitosa";
        }catch (Exception e){
        
        
    }
        return mensaje;
    }

    public String Exportar (File archivo, JTable tabla){
        String mensaje="Error en la Exportacion";
        int NumeroFila=tabla.getRowCount(),NumeroColumna=tabla.getColumnCount();
    if(archivo.getName().endsWith("xls")){
        book=new HSSFWorkbook();
    }else{
        book=new XSSFWorkbook();
    }
    Sheet hoja=book.createSheet("Hojal");
    
    try {
        for (int i = -1; i < NumeroFila; i++){
            Row fila=hoja.createRow(i+1);
            for (int j = 0; j <NumeroColumna; j++){
                Cell celda=fila.createCell(j);
                if (i==-1){
                    celda.setCellValue(String.valueOf(tabla.getColumnName(j)));
                }else{
                    celda.setCellValue(String.valueOf(tabla.getValueAt(i, j)));
                }
                book.write(new FileOutputStream(archivo));
            }
        }
        mensaje="Exportacio exitosa";
    }catch (Exception e){
    }
    return mensaje;
}
}
