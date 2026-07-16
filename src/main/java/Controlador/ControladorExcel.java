/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Controlador;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import com.mycompany.dc.Dashboard;
import Modelo.ModeloExcel;
import Modelo.GestionExcel;
import Modelo.ConexionSQL;
import Modelo.ExcelVisualizador; 
        
public class ControladorExcel {
    
    private ModeloExcel ModeloEX;
    private Dashboard VistaEX;
    private JFileChooser SelectArchivo = new JFileChooser();
    private File archivo;
    
    // --- VARIABLES DE CONTROL DE ESTADO (DICI) ---
    private int idPlantillaActiva = 1; // 1: DATOS_INST, 2: MATRICULA, 3: SALUD, 4: NOMINA
    private int idPeriodoActivo = 1;   // 1: Periodo por defecto '2025-2026'
    private String nombreSeccionActiva = "Datos Institucionales";
    private boolean enModoDetalle = false; // false = viendo lista de archivos | true = viendo celdas de un Excel

    // Variable añadida manualmente para inyectar en la vista
    public ControladorExcel controlador;

    public ControladorExcel(Dashboard VistaEX, ModeloExcel ModeloEX) {
        this.VistaEX = VistaEX;
        this.ModeloEX = ModeloEX;
        
        AgregarFiltro();
        inicializarEventos();
        
        // Al arrancar, mostrar los formatos guardados en la sección inicial
        mostrarHistorialSeccion();
        
        VistaEX.setVisible(true);
        VistaEX.setLocationRelativeTo(null);
    }

    public void AgregarFiltro() {
        SelectArchivo.setFileFilter(new FileNameExtensionFilter("Excel (*.xls)", "xls"));
        SelectArchivo.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx"));
    }

   private void inicializarEventos() {
        // Evento para el botón Importar
        VistaEX.BtImport.addActionListener(e -> accionImportar());
        
        // =========================================================================
        // 🌟 CONTROL DE CLICS UNIFICADO (DOBLE CLIC PARA ABRIR & CLIC PARA OPCIONES)
        // =========================================================================
        VistaEX.DatosExel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                // Usamos getSelectedRow() que es mucho más estable y seguro que rowAtPoint para el doble clic
                int fila = VistaEX.DatosExel.getSelectedRow();
                int columna = VistaEX.DatosExel.columnAtPoint(evt.getPoint());

                // Si el clic no es en una fila válida, detenemos
                if (fila == -1) return;

                // Convertir el índice por si la tabla está ordenada o filtrada
                int filaModelo = VistaEX.DatosExel.convertRowIndexToModel(fila);

                // -----------------------------------------------------------------
                // CASO A: DOBLE CLIC (Para abrir el documento de Excel en PESTAÑA NUEVA)
                // -----------------------------------------------------------------
                if (evt.getClickCount() == 2 && !enModoDetalle) {
                    // 🛑 PROTECCIÓN INTELIGENTE DE CLIC:
                    // Si tiene 4 columnas es porque está mostrando los resultados de búsqueda global. 
                    // De lo contrario, valida las columnas 0 (Checkbox) y 3 (Opciones) de la vista de carpetas normales.
                    if (VistaEX.DatosExel.getColumnCount() != 4 && (columna == 0 || columna == 3)) {
                        return; 
                    }

                    try {
                        // Recuperamos el modelo original que guardamos oculto en las propiedades
                        DefaultTableModel modeloOrig = (DefaultTableModel) VistaEX.DatosExel.getClientProperty("modeloOriginal_IDs");
                        
                        if (modeloOrig != null) {
                            // El ID real de la BD está en la misma fila pero del modelo original
                            Object valorId = modeloOrig.getValueAt(filaModelo, 0);
                            int idDoc = Integer.parseInt(valorId.toString());
                            
                            // El nombre está en la columna 1 de la tabla visual
                            Object valorNombre = VistaEX.DatosExel.getValueAt(fila, 1);
                            String nombreDoc = (valorNombre != null) ? valorNombre.toString() : "Formato Sin Nombre";
                            
                            // Forzamos a la tabla a detener cualquier edición visual antes de cambiar de vista
                            if (VistaEX.DatosExel.isEditing()) {
                                VistaEX.DatosExel.getCellEditor().stopCellEditing();
                            }

                            // 🔥 LLAMAMOS AL NUEVO MÉTODO DE PESTAÑAS
                            abrirDocumentoEnNuevaPestana(idDoc, nombreDoc);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(VistaEX, 
                            "Error al intentar leer el formato seleccionado: " + ex.getMessage(), 
                            "DICI - Error de Lectura", 
                            JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    }
                    return; // Fin del proceso de doble clic
                }

                // -----------------------------------------------------------------
                // CASO B: UN SOLO CLIC EN LA COLUMNA 3 (Menú de Opciones e Inteligencia de Lotes)
                // -----------------------------------------------------------------
                if (evt.getClickCount() == 1 && columna == 3 && !enModoDetalle) {
                    
                    // Aseguramos que si el usuario estaba marcando una casilla, el JTable guarde el valor real en caliente
                    if (VistaEX.DatosExel.isEditing()) {
                        VistaEX.DatosExel.getCellEditor().stopCellEditing();
                    }

                    // 1. REVISAR SI HAY CASILLAS SELECCIONADAS EN LA COLUMNA 0
                    java.util.List<Integer> indicesFilasChecks = new java.util.ArrayList<>();
                    for (int i = 0; i < VistaEX.DatosExel.getRowCount(); i++) {
                        Object valorCheck = VistaEX.DatosExel.getValueAt(i, 0);
                        if (valorCheck instanceof Boolean && (Boolean) valorCheck) {
                            indicesFilasChecks.add(i);
                        }
                    }

                    boolean tieneSeleccionMultiple = !indicesFilasChecks.isEmpty();

                    // 2. RECUPERAR LOS DATOS REALES DE LOS IDS DESDE EL MODELO OCULTO
                    DefaultTableModel modeloOrig = (DefaultTableModel) VistaEX.DatosExel.getClientProperty("modeloOriginal_IDs");
                    if (modeloOrig == null) return;
                    
                    int[] idsProcesar;
                    String mensajeConfirmacionEliminar;
                    
                    if (tieneSeleccionMultiple) {
                        // Si marcó varias casillas, recolectamos todos sus IDs convirtiendo los índices correctamente
                        idsProcesar = new int[indicesFilasChecks.size()];
                        for (int k = 0; k < indicesFilasChecks.size(); k++) {
                            int filaCheck = indicesFilasChecks.get(k);
                            int filaCheckModelo = VistaEX.DatosExel.convertRowIndexToModel(filaCheck);
                            idsProcesar[k] = Integer.parseInt(modeloOrig.getValueAt(filaCheckModelo, 0).toString());
                        }
                        mensajeConfirmacionEliminar = "¿Seguro que deseas eliminar los (" + idsProcesar.length + ") formatos seleccionados?\nEsta acción borrará de SQLite todos sus datos por completo.";
                    } else {
                        // Si no marcó nada, procesamos únicamente la fila donde cliqueó las opciones
                        int idDocIndividual = Integer.parseInt(modeloOrig.getValueAt(filaModelo, 0).toString());
                        idsProcesar = new int[]{idDocIndividual};
                        String nombreActual = VistaEX.DatosExel.getValueAt(fila, 1).toString();
                        mensajeConfirmacionEliminar = "¿Seguro que deseas eliminar por completo el formato '" + nombreActual + "'?\nEsta acción no se puede deshacer.";
                    }

                    // 3. CREAR EL MENÚ FLOTANTE DINÁMICO
                    JPopupMenu menuFlotante = new JPopupMenu();
                    JMenuItem itemEditar = new JMenuItem("✏️ Editar Nombre");
                    JMenuItem itemMover = new JMenuItem("📦 Mover Selección (" + idsProcesar.length + ")");
                    JMenuItem itemExportar = new JMenuItem("📥 Exportar Selección (" + idsProcesar.length + ")"); // NUEVO
                    JMenuItem itemEliminar = new JMenuItem("🗑️ Eliminar Selección (" + idsProcesar.length + ")");

                    if (tieneSeleccionMultiple) {
                        itemEditar.setEnabled(false);
                        itemEditar.setText("✏️ Editar Nombre (Solo individual)");
                    }

                    // --- ACCIÓN: ✏️ EDICIÓN INDIVIDUAL ---
                    itemEditar.addActionListener(e -> {
                        String nombreActual = VistaEX.DatosExel.getValueAt(fila, 1).toString();
                        String nuevoNombre = JOptionPane.showInputDialog(VistaEX, "Modificar nombre del formato:", nombreActual);
                        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
                            Connection con = ConexionSQL.getConexion();
                            if (con != null) {
                                try (PreparedStatement ps = con.prepareStatement("UPDATE documentos_planilla SET nombre_archivo_origen = ? WHERE id_documento = ?")) {
                                    ps.setString(1, nuevoNombre.trim());
                                    ps.setInt(2, idsProcesar[0]);
                                    ps.executeUpdate();
                                    JOptionPane.showMessageDialog(VistaEX, "¡Nombre actualizado con éxito!");
                                    mostrarHistorialSeccion();
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(VistaEX, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    });

                    // --- ACCIÓN: 📥 EXPORTAR CON CONTRASEÑA ---
                    itemExportar.addActionListener(e -> {
                        ejecutarExportacionArchivos(idsProcesar);
                    });

                    // --- ACCIÓN: 🗑️ ELIMINACIÓN CON CONTRASEÑA ---
                    itemEliminar.addActionListener(e -> {
                        // 🔒 Validamos la contraseña primero
                        if (!solicitarConfirmacionContrasena()) {
                            return; // Cancela la eliminación si falla la clave
                        }

                        int respuesta = JOptionPane.showConfirmDialog(VistaEX, mensajeConfirmacionEliminar, "Eliminar Formatos", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        
                        if (respuesta == JOptionPane.YES_OPTION) {
                            Connection con = ConexionSQL.getConexion();
                            if (con != null) {
                                try {
                                    con.setAutoCommit(false); 
                                    
                                    for (int idDoc : idsProcesar) {
                                        try (PreparedStatement ps1 = con.prepareStatement("DELETE FROM valores_celda WHERE id_documento = ?")) {
                                            ps1.setInt(1, idDoc);
                                            ps1.executeUpdate();
                                        }
                                        try (PreparedStatement ps2 = con.prepareStatement("DELETE FROM documentos_planilla WHERE id_documento = ?")) {
                                            ps2.setInt(1, idDoc);
                                            ps2.executeUpdate();
                                        }
                                    }
                                    
                                    con.commit(); 
                                    JOptionPane.showMessageDialog(VistaEX, "¡Proceso completado! Se eliminaron (" + idsProcesar.length + ") formatos correctamente.");
                                    mostrarHistorialSeccion(); 
                                    
                                } catch (Exception ex) {
                                    try { con.rollback(); } catch (java.sql.SQLException s) {}
                                    JOptionPane.showMessageDialog(VistaEX, "Error al ejecutar el borrado: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    });

                    // --- ACCIÓN: 📦 MOVER EN LOTE / INDIVIDUAL ---
                    itemMover.addActionListener(e -> {
                        ejecutarMudanzaDeArchivos(idsProcesar);
                    });

                    // Armamos el menú en orden
                    menuFlotante.add(itemEditar);
                    menuFlotante.add(itemMover);
                    menuFlotante.add(itemExportar);
                    menuFlotante.addSeparator();
                    menuFlotante.add(itemEliminar);

                    menuFlotante.show(VistaEX.DatosExel, (int) evt.getPoint().getX(), (int) evt.getPoint().getY());
                }
            }
        });

        // =========================================================================
        // 🔍 MONITOREO EN TIEMPO REAL DE LA BARRA DE BÚSQUEDA
        // =========================================================================
        VistaEX.txtBuscar.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                buscarGlobalmente(VistaEX.txtBuscar.getText());
            }
        });
    
       // Evento en tiempo real al escribir en la barra de búsqueda
VistaEX.txtBuscar.addKeyListener(new java.awt.event.KeyAdapter() {
    @Override
    public void keyReleased(java.awt.event.KeyEvent evt) {
        buscarGlobalmente(VistaEX.txtBuscar.getText());
    }
});
}
    // --- LÓGICA PARA CAMBIAR DE CARPETA ---
    public void cambiarSeccion(int idPlantilla, String nombreSeccion) {
        this.idPlantillaActiva = idPlantilla;
        this.nombreSeccionActiva = nombreSeccion;
        this.enModoDetalle = false;
        
        // Bloquear el botón exportar temporalmente porque se está visualizando la lista de formatos
//        VistaEX.BtExport.setEnabled(false); 
        mostrarHistorialSeccion();
    }

    // ==================== MOSTRAR HISTORIAL DE SECCIÓN (LIMPIADO) ====================
    private void mostrarHistorialSeccion() {
        Connection con = ConexionSQL.getConexion();
        if (con != null) {
            // 🔥 PASO 1: LIMPIAR LA TABLA COMPLETAMENTE
            String[] nuevasColumnas = {"Selección", "Nombre del Formato", "Fecha de Registro", "Acciones"};
            DefaultTableModel modeloVacio = new DefaultTableModel(null, nuevasColumnas) {
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    if (columnIndex == 0) {
                        return Boolean.class; // Para los checkboxes
                    }
                    return super.getColumnClass(columnIndex);
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 0 || column == 3;
                }
            };
            
            VistaEX.DatosExel.setModel(modeloVacio);
            VistaEX.DatosExel.revalidate();
            VistaEX.DatosExel.repaint();
            
            // 🔥 PASO 2: OBTENER LOS DATOS REALES DE LA BASE DE DATOS
            DefaultTableModel modeloOriginal = GestionExcel.obtenerListaDocumentos(con, idPlantillaActiva, idPeriodoActivo);
            
            if (modeloOriginal != null && modeloOriginal.getRowCount() > 0) {
                // 🔥 PASO 3: LLENAR EL MODELO CON LOS DATOS
                for (int i = 0; i < modeloOriginal.getRowCount(); i++) {
                    Object idBD = modeloOriginal.getValueAt(i, 0);     
                    Object nombreBD = modeloOriginal.getValueAt(i, 1); 
                    Object fechaBD = modeloOriginal.getValueAt(i, 2);  

                    modeloVacio.addRow(new Object[]{
                        false,              
                        nombreBD,           
                        fechaBD,            
                        "⚙️ Opciones..."   
                    });
                }
                
                // 🔥 PASO 4: GUARDAR LOS IDs ORIGINALES EN UNA PROPIEDAD OCULTA
                VistaEX.DatosExel.putClientProperty("modeloOriginal_IDs", modeloOriginal);
                
                // 🔥 PASO 5: AJUSTAR EL ANCHO DE LAS COLUMNAS
                VistaEX.DatosExel.getColumnModel().getColumn(0).setPreferredWidth(70);   
                VistaEX.DatosExel.getColumnModel().getColumn(1).setPreferredWidth(250);  
                VistaEX.DatosExel.getColumnModel().getColumn(2).setPreferredWidth(150);  
                VistaEX.DatosExel.getColumnModel().getColumn(3).setPreferredWidth(110);  
                
            } else {
                modeloVacio.addRow(new Object[]{
                    false,
                    "No hay formatos guardados en esta sección",
                    "",
                    ""
                });
            }

            // 🔥 PASO 6: ACTUALIZAR LA VISTA
            VistaEX.DatosExel.revalidate();
            VistaEX.DatosExel.repaint();
            
            // 🔥 PASO 7: ACTUALIZAR LA ETIQUETA DE INFORMACIÓN
     //       VistaEX.Info.setText("📁 Carpeta: " + nombreSeccionActiva.toUpperCase() + 
       //         " | Use las casillas para mover en lote o clique 'Opciones'.");
            
            // 🔥 PASO 8: CAMBIAR EL ESTADO A MODO HISTORIAL
            enModoDetalle = false;
//            VistaEX.BtExport.setEnabled(false);
            
            System.out.println("✅ Historial mostrado para sección: " + nombreSeccionActiva);
            
        } else {
            System.err.println("❌ Error: No se pudo establecer conexión para actualizar el historial.");
            JOptionPane.showMessageDialog(VistaEX, 
                "Error de conexión al cargar el historial.", 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

private void accionImportar() {
    // 1. CREAR EL SELECTOR NATIVO DEL SISTEMA OPERATIVO
    java.awt.FileDialog fileDialog = new java.awt.FileDialog(VistaEX, "DICI - Seleccionar Formato(s)", java.awt.FileDialog.LOAD);
    
    // Habilitar la selección múltiple nativa (¡permite arrastrar el mouse perfectamente!)
    fileDialog.setMultipleMode(true);
    
    // Filtrar para que solo muestre archivos de Excel (.xls y .xlsx)
    fileDialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".xls") || name.toLowerCase().endsWith(".xlsx"));
    
    // Mostrar el diálogo
    fileDialog.setVisible(true);
    
    // Obtener los archivos seleccionados
    File[] archivosSeleccionados = fileDialog.getFiles();

    // Si el usuario cierra el diálogo o no selecciona nada, salimos de inmediato
    if (archivosSeleccionados == null || archivosSeleccionados.length == 0) {
        return;
    }

    Connection con = ConexionSQL.getConexion();
    if (con == null) {
        JOptionPane.showMessageDialog(VistaEX, "Error de conexión a la base de datos.", 
            "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Limpieza preventiva inicial del JTable
    DefaultTableModel modeloVacio = new DefaultTableModel();
    VistaEX.DatosExel.setModel(modeloVacio);
    VistaEX.DatosExel.revalidate();
    VistaEX.DatosExel.repaint();

    int importadosConExito = 0;

    // 2. RECORRER TODOS LOS ARCHIVOS SELECCIONADOS
    for (File archivoActual : archivosSeleccionados) {
        // Nombre sugerido por defecto (nombre del archivo sin extensión)
        String nombreFinal = archivoActual.getName().replaceFirst("[.][^.]+$", "").trim();

        // Verificar si ya existe en la sección activa de la Base de Datos
        int idDocExistente = GestionExcel.obtenerIdDocumentoPorNombre(con, nombreFinal, idPlantillaActiva, idPeriodoActivo);

        if (idDocExistente != -1) {
            String[] opcionesConflicto = {"Reemplazar", "Renombrar", "Omitir"};
            int eleccion = JOptionPane.showOptionDialog(
                    VistaEX,
                    "El formato '" + nombreFinal + "' ya existe en esta sección.\n¿Qué desea hacer con el archivo '" + archivoActual.getName() + "'?",
                    "Conflicto de Formato",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    opcionesConflicto,
                    opcionesConflicto[0]
            );

            if (eleccion == 0) { // REEMPLAZAR
                try {
                    GestionExcel.importarExcelAEAV(con, archivoActual, idPlantillaActiva, idDocExistente);
                    GestionExcel.guardarArchivoOriginal(con, archivoActual, idDocExistente);
                    importadosConExito++;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VistaEX, "Error al reemplazar '" + nombreFinal + "': " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else if (eleccion == 1) { // RENOMBRAR
                String nuevoNombre = (String) JOptionPane.showInputDialog(
                        VistaEX,
                        "Ingrese un nuevo nombre para el formato:",
                        "Renombrar Formato",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        null,
                        nombreFinal + "_Copia"
                );

                if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
                    guardarNuevoRegistro(con, archivoActual, nuevoNombre.trim());
                    importadosConExito++;
                }
            }

        } else {
            // Si no existe conflicto, lo guarda directamente
            guardarNuevoRegistro(con, archivoActual, nombreFinal);
            importadosConExito++;
        }
    }

    // 3. FINALIZAR Y REFRESCAR LA INTERFAZ
    enModoDetalle = false; 
    mostrarHistorialSeccion();

    if (importadosConExito > 0) {
        JOptionPane.showMessageDialog(VistaEX, "✅ ¡Proceso completado!\nSe importaron (" + importadosConExito + ") archivo(s) con éxito en '" + nombreSeccionActiva + "'.", 
            "Importación Exitosa", JOptionPane.INFORMATION_MESSAGE);
    }
}

// Método auxiliar de soporte para guardar registros limpios y evitar duplicación de código
private void guardarNuevoRegistro(Connection con, File archivoActual, String nombreRegistro) {
    try {
        String sqlDoc = "INSERT INTO documentos_planilla (id_plantilla, id_periodo, nombre_archivo_origen) VALUES (?, ?, ?);";
        try (PreparedStatement psDoc = con.prepareStatement(sqlDoc, PreparedStatement.RETURN_GENERATED_KEYS)) {
            psDoc.setInt(1, idPlantillaActiva);
            psDoc.setInt(2, idPeriodoActivo);
            psDoc.setString(3, nombreRegistro);
            psDoc.executeUpdate();
            
            try (java.sql.ResultSet rsKey = psDoc.getGeneratedKeys()) {
                if (rsKey.next()) {
                    int nuevoIdDoc = rsKey.getInt(1);
                    GestionExcel.importarExcelAEAV(con, archivoActual, idPlantillaActiva, nuevoIdDoc);
                    GestionExcel.guardarArchivoOriginal(con, archivoActual, nuevoIdDoc);
                }
            }
        }
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(VistaEX, "Error al guardar el formato '" + nombreRegistro + "': " + ex.getMessage(), 
            "Error", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }
}
    // ==================== MÉTODO MUDANZA DE ARCHIVOS (AÑADIDO PARA EVITAR ERRORES) ====================
  // ==================== MÉTODO MUDANZA DE ARCHIVOS DINÁMICO (SOPORTA BOTONES DEL USUARIO) ====================
private void ejecutarMudanzaDeArchivos(int[] idsProcesar) {
    if (idsProcesar == null || idsProcesar.length == 0) {
        JOptionPane.showMessageDialog(VistaEX, "No hay archivos seleccionados para mover.", 
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    Connection con = ConexionSQL.getConexion();
    if (con == null) {
        JOptionPane.showMessageDialog(VistaEX, "Error de conexión a la base de datos.", 
                "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Listas dinámicas para almacenar lo que recuperemos de la Base de Datos
    java.util.List<String> nombresSecciones = new java.util.ArrayList<>();
    java.util.List<Integer> idsSecciones = new java.util.ArrayList<>();

    // 1. Obtener TODAS las plantillas (secciones) disponibles en la base de datos
    String sqlObtenerSecciones = "SELECT id_plantilla, nombre_plantilla FROM plantillas ORDER BY id_plantilla ASC;";
    try (PreparedStatement psSecciones = con.prepareStatement(sqlObtenerSecciones);
         java.sql.ResultSet rs = psSecciones.executeQuery()) {
        
        while (rs.next()) {
            idsSecciones.add(rs.getInt("id_plantilla"));
            nombresSecciones.add(rs.getString("nombre_plantilla"));
        }
        
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(VistaEX, "Error al cargar las secciones de destino: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Validar si encontramos secciones en la BD por si acaso
    if (nombresSecciones.isEmpty()) {
        JOptionPane.showMessageDialog(VistaEX, "No se encontraron secciones de destino en la base de datos.", 
                "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Convertir la lista dinámica a un arreglo String[] tradicional para el JOptionPane
    String[] seccionesDestino = nombresSecciones.toArray(new String[0]);

    // 2. Desplegar el selector JComboBox con todas las opciones dinámicas encontradas
    String seleccion = (String) JOptionPane.showInputDialog(
            VistaEX,
            "Seleccione la sección de destino para los (" + idsProcesar.length + ") formatos:",
            "📦 DICI - Mover Archivos en Lote",
            JOptionPane.QUESTION_MESSAGE,
            null,
            seccionesDestino,
            seccionesDestino[0]
    );

    // Si el usuario cancela o cierra la ventana, salimos de inmediato
    if (seleccion == null) return;

    // 3. Obtener el ID de la plantilla seleccionada buscando su posición en la lista
    int indexSeleccionado = nombresSecciones.indexOf(seleccion);
    int nuevoIdPlantilla = idsSecciones.get(indexSeleccionado);

    // Protección: Evitar mover los archivos a la misma sección activa actual
    if (nuevoIdPlantilla == idPlantillaActiva) {
        JOptionPane.showMessageDialog(VistaEX, 
                "Los archivos seleccionados ya se encuentran en la sección '" + seleccion + "'.", 
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    // 4. Ejecutar la actualización masiva en SQLite usando Batch para máxima velocidad
    String sqlMover = "UPDATE documentos_planilla SET id_plantilla = ? WHERE id_documento = ?";
    try {
        // Activamos modo transaccional seguro
        con.setAutoCommit(false);

        try (PreparedStatement psMover = con.prepareStatement(sqlMover)) {
            for (int idDoc : idsProcesar) {
                psMover.setInt(1, nuevoIdPlantilla);
                psMover.setInt(2, idDoc);
                psMover.addBatch(); // Empaquetar operaciones en memoria
            }
            psMover.executeBatch(); // Enviar todas a la BD en una sola transacción
        }

        con.commit(); // Consolidar permanentemente en disco
        
        JOptionPane.showMessageDialog(VistaEX, 
                "¡Mudanza exitosa! Se transfirieron (" + idsProcesar.length + ") archivos a la sección [" + seleccion + "].", 
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
        
        // 🔄 Refrescamos la tabla para que se actualice la vista
        mostrarHistorialSeccion();

    } catch (Exception ex) {
        try { con.rollback(); } catch (java.sql.SQLException s) {}
        JOptionPane.showMessageDialog(VistaEX, "Error crítico al mover los lotes: " + ex.getMessage(), 
                "Error de Operación", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }
}

    // ==================== MÉTODO ABRIR DETALLE DE DOCUMENTO (INTEGRADO DE FORMA SEGURA) ====================
    private void abrirDetalleDocumento(int idDocumento, String nombreDocumento) {
        System.out.println("=== ABRIENDO DOCUMENTO: " + nombreDocumento + " (ID: " + idDocumento + ") ===");
        
        Connection con = ConexionSQL.getConexion();
        if (con == null) {
            JOptionPane.showMessageDialog(VistaEX, "Error de conexión a la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            File archivoTemp = GestionExcel.recuperarArchivoOriginal(con, idDocumento);
            
            if (archivoTemp == null) {
                JOptionPane.showMessageDialog(VistaEX, 
                    "Este documento fue importado antes de la actualización.\n" +
                    "Por favor, impórtelo nuevamente para poder visualizarlo con formato completo.\n\n" +
                    "Mientras tanto, se mostrarán los datos disponibles.", 
                    "Documento Antiguo", JOptionPane.WARNING_MESSAGE);
                
                DefaultTableModel modelo = GestionExcel.cargarDetalleDocumento(con, idDocumento);
                if (modelo != null && modelo.getRowCount() > 0) {
                    VistaEX.DatosExel.setModel(modelo);
//                    VistaEX.Info.setText("📊 Viendo (vista básica): " + nombreDocumento);
                } else {
                    JOptionPane.showMessageDialog(VistaEX, "No se encontraron datos para este documento.", "Sin Datos", JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }
            
            ExcelVisualizador visualizador = new ExcelVisualizador();
            JTable tablaExcel = visualizador.cargarExcel(archivoTemp);
            
            JScrollPane scrollPane = new JScrollPane(tablaExcel);
            scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new java.awt.Color(21, 101, 192), 2),
                "📊 " + nombreDocumento,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new java.awt.Font("Arial", java.awt.Font.BOLD, 14),
                new java.awt.Color(21, 101, 192)
            ));
            scrollPane.getViewport().setBackground(java.awt.Color.WHITE);
            
            JPanel panelAcciones = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
            panelAcciones.setBackground(java.awt.Color.WHITE);
            panelAcciones.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            JButton btnExportar = new JButton("📥 Exportar");
            btnExportar.setBackground(new java.awt.Color(45, 85, 154));
            btnExportar.setForeground(java.awt.Color.WHITE);
            btnExportar.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            btnExportar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            btnExportar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            btnExportar.setFocusPainted(false);
            btnExportar.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Guardar archivo como...");
                fileChooser.setSelectedFile(new File(nombreDocumento));
                fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos Excel (*.xlsx, *.xls)", "xlsx", "xls"));
                
                if (fileChooser.showSaveDialog(VistaEX) == JFileChooser.APPROVE_OPTION) {
                    try {
                        File archivoDestino = fileChooser.getSelectedFile();
                        String ruta = archivoDestino.getAbsolutePath();
                        if (!ruta.toLowerCase().endsWith(".xlsx") && !ruta.toLowerCase().endsWith(".xls")) {
                            archivoDestino = new File(ruta + ".xlsx");
                        }
                        java.nio.file.Files.copy(archivoTemp.toPath(), archivoDestino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        JOptionPane.showMessageDialog(VistaEX, "✅ Archivo exportado exitosamente.", "Exportación Exitosa", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(VistaEX, "Error al exportar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            panelAcciones.add(btnExportar);
            
            JButton btnCerrar = new JButton("✖ Cerrar");
            btnCerrar.setBackground(new java.awt.Color(180, 50, 50));
            btnCerrar.setForeground(java.awt.Color.WHITE);
            btnCerrar.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            btnCerrar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            btnCerrar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            btnCerrar.setFocusPainted(false);
            btnCerrar.addActionListener(e -> { mostrarHistorialSeccion(); });
            panelAcciones.add(btnCerrar);
            
            JButton btnAbrirExcel = new JButton("📂 Abrir en Excel");
            btnAbrirExcel.setBackground(new java.awt.Color(33, 150, 83));
            btnAbrirExcel.setForeground(java.awt.Color.WHITE);
            btnAbrirExcel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            btnAbrirExcel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            btnAbrirExcel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            btnAbrirExcel.setFocusPainted(false);
            btnAbrirExcel.addActionListener(e -> {
                try {
                    File tempCopy = File.createTempFile("dici_abrir_", ".xlsx");
                    tempCopy.deleteOnExit();
                    java.nio.file.Files.copy(archivoTemp.toPath(), tempCopy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(tempCopy);
                    } else {
                        JOptionPane.showMessageDialog(VistaEX, "Abra el archivo manualmente desde la exportación.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VistaEX, "Error al abrir: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            panelAcciones.add(btnAbrirExcel);
            
            JPanel panelPrincipal = new JPanel(new java.awt.BorderLayout());
            panelPrincipal.setBackground(java.awt.Color.WHITE);
            panelPrincipal.add(scrollPane, java.awt.BorderLayout.CENTER);
            panelPrincipal.add(panelAcciones, java.awt.BorderLayout.SOUTH);
            
            VistaEX.DatosExel.removeAll();
            VistaEX.DatosExel.setLayout(new java.awt.BorderLayout());
            VistaEX.DatosExel.add(panelPrincipal, java.awt.BorderLayout.CENTER);
            VistaEX.DatosExel.revalidate();
            VistaEX.DatosExel.repaint();
            
            enModoDetalle = true;
//            VistaEX.BtExport.setEnabled(true);
//            VistaEX.Info.setText("📊 Viendo: " + nombreDocumento + " | Filas: " + tablaExcel.getRowCount());
            
            VistaEX.DatosExel.putClientProperty("archivoTemporal", archivoTemp);
            VistaEX.DatosExel.putClientProperty("idDocumentoActual", idDocumento);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(VistaEX, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void abrirDocumentoEnNuevaPestana(int idDocumento, String nombreDocumento) {
    Connection con = ConexionSQL.getConexion();
    if (con == null) {
        JOptionPane.showMessageDialog(VistaEX, "Error de conexión a la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // 1. Evitar duplicados
    int totalPestanas = VistaEX.contenedorPestanas.getTabCount();
    for (int i = 0; i < totalPestanas; i++) {
        if (VistaEX.contenedorPestanas.getTitleAt(i).equals(nombreDocumento)) {
            VistaEX.contenedorPestanas.setSelectedIndex(i);
            return;
        }
    }

    File archivoTemp = null;
    try {
        // 2. Intento de recuperación del archivo original
        System.out.println("🔍 Buscando archivo en BD para ID: " + idDocumento);
        archivoTemp = GestionExcel.recuperarArchivoOriginal(con, idDocumento);
        
        if (archivoTemp == null || !archivoTemp.exists()) {
            JOptionPane.showMessageDialog(VistaEX, 
                "El archivo temporal no se pudo crear o no existe en el disco.\nID: " + idDocumento, 
                "Error de Archivo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        System.out.println("📂 Archivo temporal listo en: " + archivoTemp.getAbsolutePath());

        // 3. Intento de procesamiento con tu ExcelVisualizador
        System.out.println("📊 Pasando archivo a ExcelVisualizador...");
        Modelo.ExcelVisualizador visualizador = new Modelo.ExcelVisualizador();
        JTable tablaConFormato = visualizador.cargarExcel(archivoTemp);

        if (tablaConFormato == null) {
            JOptionPane.showMessageDialog(VistaEX, 
                "ExcelVisualizador devolvió una tabla nula (null).", 
                "Error de Renderizado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 4. Montar los contenedores gráficos
        JPanel panelPestana = new JPanel(new java.awt.BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tablaConFormato);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panelPestana.add(scrollPane, java.awt.BorderLayout.CENTER);

        // 5. Agregar al JTabbedPane
        VistaEX.contenedorPestanas.addTab(nombreDocumento, panelPestana);
        
        int nuevoIndice = VistaEX.contenedorPestanas.getTabCount() - 1;
        VistaEX.contenedorPestanas.setSelectedIndex(nuevoIndice);

        configurarBotonCerrarPestana(nuevoIndice, nombreDocumento);
        System.out.println("✅ Pestaña '" + nombreDocumento + "' abierta con éxito.");

    } catch (NullPointerException npe) {
        System.err.println("❌ Error de apuntador nulo interno:");
        npe.printStackTrace();
        JOptionPane.showMessageDialog(VistaEX, 
            "Error: Se intentó leer un objeto nulo en el formato visual.\nDetalle: " + npe.getMessage(), 
            "NullPointerException", JOptionPane.ERROR_MESSAGE);
    } catch (Exception e) {
        System.err.println("❌ Error crítico en el procesamiento visual:");
        e.printStackTrace();
        JOptionPane.showMessageDialog(VistaEX, 
            "Error al procesar el formato visual.\n\nClase del Error: " + e.getClass().getSimpleName() + 
            "\nDetalle: " + e.getMessage() + "\n\nRevisa la consola de NetBeans para ver el rastro completo.", 
            "Error de Procesamiento", JOptionPane.ERROR_MESSAGE);
    }
}
   
// ==================== VALIDACIÓN DE SEGURIDAD POR CONTRASEÑA ====================
private boolean solicitarConfirmacionContrasena() {
    JPasswordField pf = new JPasswordField();
    int okCancell = JOptionPane.showConfirmDialog(
            VistaEX, 
            pf, 
            "🔒 Ingrese su contraseña para confirmar la operación:", 
            JOptionPane.OK_CANCEL_OPTION, 
            JOptionPane.PLAIN_MESSAGE
    );

    if (okCancell == JOptionPane.OK_OPTION) {
        String passwordIngresada = new String(pf.getPassword());
        if (passwordIngresada.trim().isEmpty()) {
            JOptionPane.showMessageDialog(VistaEX, "La contraseña no puede estar vacía.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // 1. Encriptamos la contraseña ingresada en SHA-256
        String hashIngresado = Modelo.baseInicial.encriptarSHA256(passwordIngresada);

        Connection con = ConexionSQL.getConexion();
        if (con != null) {
            // Buscamos si existe algún usuario en la base de datos que tenga esta contraseña
            String sql = "SELECT username FROM usuarios WHERE password = ?;";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, hashIngresado);
                
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // ¡Encontrado! Al menos un usuario registrado tiene esta contraseña
                        System.out.println("✅ Acceso autorizado para el usuario: " + rs.getString("username"));
                        return true; 
                    }
                }
            } catch (Exception ex) {
                System.err.println("❌ Error al validar clave: " + ex.getMessage());
            }
        }
        JOptionPane.showMessageDialog(VistaEX, "Contraseña incorrecta. Operación cancelada.", "Seguridad", JOptionPane.ERROR_MESSAGE);
    }
    return false; 
}
    // ==================== BÚSQUEDA GENERAL MULTI-SECCIÓN (AÑADIDO) ====================
public void buscarGlobalmente(String consulta) {
    // Si la barra de búsqueda se vacía, restauramos la vista de la sección/carpeta activa
    if (consulta == null || consulta.trim().isEmpty()) {
        mostrarHistorialSeccion();
        return;
    }

    Connection con = ConexionSQL.getConexion();
    if (con == null) {
        System.err.println("❌ Error: Sin conexión para la búsqueda.");
        return;
    }

    // Cambiamos el estado para evitar comportamientos de carpetas normales
    this.enModoDetalle = false;

    // 1. Estructuramos el modelo visual para los resultados de la búsqueda global
    String[] columnasResultados = {"Ubicación (Sección)", "Nombre del Formato", "Coincidencia Encontrada", "Fila"};
    DefaultTableModel modeloResultados = new DefaultTableModel(null, columnasResultados) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false; // Ninguna celda de la búsqueda es editable directamente
        }
    };

    // 2. Modelo oculto donde guardaremos los IDs reales de los documentos para poder abrirlos con doble clic
    DefaultTableModel modeloOcultoIDs = new DefaultTableModel(null, new String[]{"id_documento", "nombre_documento"});

    // 3. Consulta SQL cruzada optimizada que busca en TODAS las secciones/botones a la vez
    String sqlBusqueda = """
        SELECT 
            p.nombre_plantilla, 
            d.id_documento, 
            d.nombre_archivo_origen, 
            v.valor_texto, 
            v.numero_fila
        FROM valores_celda v
        JOIN documentos_planilla d ON v.id_documento = d.id_documento
        JOIN plantillas p ON d.id_plantilla = p.id_plantilla
        WHERE v.valor_texto LIKE ? AND d.id_periodo = ?
        LIMIT 100; -- Limitamos para garantizar respuesta instantánea en la interfaz
    """;

    try (PreparedStatement ps = con.prepareStatement(sqlBusqueda)) {
        ps.setString(1, "%" + consulta.trim() + "%");
        ps.setInt(2, idPeriodoActivo);

        try (java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String seccion = rs.getString("nombre_plantilla");
                int idDoc = rs.getInt("id_documento");
                String nombreDoc = rs.getString("nombre_archivo_origen");
                String textoCoincidente = rs.getString("valor_texto");
                int filaExcel = rs.getInt("numero_fila");

                // Mapeamos el nombre técnico de la plantilla a un formato más amigable si lo deseas
                String seccionAmigable = seccion;
                switch (seccion) {
                    case "DATOS_INST" -> seccionAmigable = "📁 Datos Inst.";
                    case "MATRICULA"  -> seccionAmigable = "📁 Matrícula";
                    case "SALUD"      -> seccionAmigable = "📁 Salud y Bienes";
                    case "NOMINA"     -> seccionAmigable = "📁 Nómina RAC";
                }

                // Agregamos el registro visual de coincidencia
                modeloResultados.addRow(new Object[]{
                    seccionAmigable,
                    nombreDoc,
                    textoCoincidente,
                    "Fila " + (filaExcel + 1) // El +1 es porque en Excel las filas visuales inician en 1 y en base de datos en 0
                });

                // Almacenamos el ID de forma paralela en nuestro modelo oculto de control
                modeloOcultoIDs.addRow(new Object[]{
                    idDoc,
                    nombreDoc
                });
            }
        }

        // 4. Inyectamos los resultados en el JTable de tu Dashboard
        VistaEX.DatosExel.setModel(modeloResultados);
        
        // Guardamos los ID en las propiedades del JTable para que el MouseListener pueda leerlos
        VistaEX.DatosExel.putClientProperty("modeloOriginal_IDs", modeloOcultoIDs);
        
        // Ajustamos los anchos de columnas del buscador
        VistaEX.DatosExel.getColumnModel().getColumn(0).setPreferredWidth(120); // Sección
        VistaEX.DatosExel.getColumnModel().getColumn(1).setPreferredWidth(220); // Nombre de archivo
        VistaEX.DatosExel.getColumnModel().getColumn(2).setPreferredWidth(200); // Coincidencia
        VistaEX.DatosExel.getColumnModel().getColumn(3).setPreferredWidth(60);  // Fila

        VistaEX.DatosExel.revalidate();
        VistaEX.DatosExel.repaint();

    } catch (Exception ex) {
        System.err.println("❌ Error en la búsqueda global: " + ex.getMessage());
        ex.printStackTrace();
    }
}
// ==================== MÉTODO EXPORTAR INDIVIDUAL O EN LOTE ====================
private void ejecutarExportacionArchivos(int[] idsProcesar) {
    if (idsProcesar == null || idsProcesar.length == 0) {
        JOptionPane.showMessageDialog(VistaEX, "No hay archivos seleccionados para exportar.", 
                "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    // 1. SOLICITAR CONTRASEÑA ANTES DE CONTINUAR
    if (!solicitarConfirmacionContrasena()) {
        return; // Detiene el proceso si falla la contraseña
    }

    Connection con = ConexionSQL.getConexion();
    if (con == null) {
        JOptionPane.showMessageDialog(VistaEX, "Error de conexión a la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Seleccione la carpeta de destino");

    if (idsProcesar.length == 1) {
        // EXPORTAR UN SOLO ARCHIVO
        String nombreSugerido = "documento_exportado";
        // Intentamos buscar el nombre real del archivo en la base de datos
        try (PreparedStatement ps = con.prepareStatement("SELECT nombre_archivo_origen FROM documentos_planilla WHERE id_documento = ?")) {
            ps.setInt(1, idsProcesar[0]);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombreSugerido = rs.getString("nombre_archivo_origen");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        fileChooser.setSelectedFile(new File(nombreSugerido + ".xlsx"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("Archivos Excel (*.xlsx)", "xlsx"));

        if (fileChooser.showSaveDialog(VistaEX) == JFileChooser.APPROVE_OPTION) {
            try {
                File destino = fileChooser.getSelectedFile();
                File temp = GestionExcel.recuperarArchivoOriginal(con, idsProcesar[0]);
                if (temp != null) {
                    Files.copy(temp.toPath(), destino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    JOptionPane.showMessageDialog(VistaEX, "✅ Archivo exportado con éxito.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(VistaEX, "No se encontró el archivo original en la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(VistaEX, "Error al exportar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    } else {
        // EXPORTAR MULTIPLE (COMPRESIÓN EN ZIP)
        fileChooser.setSelectedFile(new File("formatos_exportados_DICI.zip"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("Archivo ZIP (*.zip)", "zip"));

        if (fileChooser.showSaveDialog(VistaEX) == JFileChooser.APPROVE_OPTION) {
            File destinoZip = fileChooser.getSelectedFile();
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(destinoZip))) {
                
                for (int idDoc : idsProcesar) {
                    String nombreArchivo = "Formato_" + idDoc;
                    // Recuperar nombre real
                    try (PreparedStatement ps = con.prepareStatement("SELECT nombre_archivo_origen FROM documentos_planilla WHERE id_documento = ?")) {
                        ps.setInt(1, idDoc);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                nombreArchivo = rs.getString("nombre_archivo_origen");
                            }
                        }
                    } catch (Exception e) {}

                    File tempFile = GestionExcel.recuperarArchivoOriginal(con, idDoc);
                    if (tempFile != null) {
                        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(nombreArchivo + ".xlsx");
                        zos.putNextEntry(entry);
                        byte[] bytes = Files.readAllBytes(tempFile.toPath());
                        zos.write(bytes, 0, bytes.length);
                        zos.closeEntry();
                    }
                }
                JOptionPane.showMessageDialog(VistaEX, "✅ Archivos exportados y empaquetados en ZIP con éxito.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(VistaEX, "Error al empaquetar exportación: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
 private void configurarBotonCerrarPestana(int indice, String titulo) {
    JPanel panelTitulo = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
    panelTitulo.setOpaque(false);
    
    JLabel lblTitulo = new JLabel(titulo);
    JButton btnCerrar = new JButton("x");
    
    // Diseño minimalista para la "x"
    btnCerrar.setBorder(null);
    btnCerrar.setContentAreaFilled(false);
    btnCerrar.setFocusable(false);
    btnCerrar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    
    // Evento de cierre
    btnCerrar.addActionListener(e -> {
        int idx = VistaEX.contenedorPestanas.indexOfTab(titulo);
        if (idx != -1) {
            VistaEX.contenedorPestanas.remove(idx);
        }
    });
    
    panelTitulo.add(lblTitulo);
    panelTitulo.add(btnCerrar);
    
    VistaEX.contenedorPestanas.setTabComponentAt(indice, panelTitulo);
}   
    
}