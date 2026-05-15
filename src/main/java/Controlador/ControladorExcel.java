/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Controlador;
import java.awt.event.ActionEvent;
import java.awt.event.AWTEventListener;
import java.io.*;
import com.mycompany.dc.Dashboard;
import Modelo.ModeloExcel;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;

public class ControladorExcel implements ActionListener {
    
    ModeloExcel ModeloEX=new ModeloExcel();
    Dashboard VistaEX=new Dashboard();
    JFileChooser SelectArchivo=new JFileChooser();
    File archivo;
    int contador=0;
    
    public ControladorExcel(Dashboard VistaEX, ModeloExcel ModeloEX){
        this.VistaEX=VistaEX;
        this.ModeloEX=ModeloEX;
        this.VistaEX.BtImport.addActionListener(this);
        this.VistaEX.BtExport.addActionListener(this);
        VistaEX.setVisible(true);
        VistaEX.setLocationRelativeTo(null);
        
    }
public void AgregarFiltro(){
    SelectArchivo.setFileFilter(new FileNameExtensionFilter("Excel (*.xls)","xls"));
    SelectArchivo.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)","xlsx"));
}
   @Override
public void actionPerformed(ActionEvent e) {
    contador++;
    if (contador == 1) AgregarFiltro();
    
    if (e.getSource() == VistaEX.BtImport) {
        if (SelectArchivo.showDialog(null, "Seleccionar Archivo") == JFileChooser.APPROVE_OPTION) {
            
          
            archivo = SelectArchivo.getSelectedFile();

            
            try {
                Path path = archivo.toPath();
                BasicFileAttributes atributos = Files.readAttributes(path, BasicFileAttributes.class);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

                String nombre = archivo.getName();
                String creacion = sdf.format(atributos.creationTime().toMillis());
                String modificacion = sdf.format(atributos.lastModifiedTime().toMillis());

                String infoTexto = "Archivo: " + nombre + " | Creado: " + creacion + " | Modificado: " + modificacion;
                VistaEX.Info.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                VistaEX.Info.setText(infoTexto);
                VistaEX.getContentPane().setComponentZOrder(VistaEX.Info, 0);
                VistaEX.Info.revalidate();
                VistaEX.Info.repaint();

            } catch (Exception ex) {
                System.err.println("Error al leer metadatos: " + ex.getMessage());
            }        
            if (archivo.getName().endsWith("xls") || archivo.getName().endsWith("xlsx")) {
                JOptionPane.showMessageDialog(null, ModeloEX.Importar(archivo, VistaEX.DatosExel));
            } else {
                JOptionPane.showMessageDialog(null, "SELECCIONE UN FORMATO VALIDO");
            }
        }
    }
   

       if (e.getSource()==VistaEX.BtExport){
          if(SelectArchivo.showDialog(null, "Seleccionar Archivo")== JFileChooser.APPROVE_OPTION){
            archivo=SelectArchivo.getSelectedFile();
            //ALF+124
            if(archivo.getName().endsWith("xls")||archivo.getName().endsWith("xlsx")){
              JOptionPane.showMessageDialog(null,ModeloEX.Exportar(archivo, VistaEX.DatosExel));
            }else{
                JOptionPane.showMessageDialog(null,"SELECCIONE UN FORMATO VALIDO");
          }
          }
      }
    }
    
}
