package Modelo;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.JOptionPane;
import org.json.JSONArray;
import org.json.JSONObject;

public class RespaldoUtil {

    public static void exportarRespaldo(Connection con, File archivoSalida, String password) throws Exception {
        File tempZip = File.createTempFile("respaldo_temp_", ".zip");
        tempZip.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            JSONObject estructura = exportarEstructuraCompleta(con);

            ZipEntry jsonEntry = new ZipEntry("estructura.json");
            zos.putNextEntry(jsonEntry);
            zos.write(estructura.toString(2).getBytes("UTF-8"));
            zos.closeEntry();

            exportarTodosLosDocumentos(con, zos);
            exportarDatosCeldas(con, zos);
            exportarConfiguracionBotones(zos);

            System.out.println("✅ Respaldo exportado con éxito");
        }

        CifradoUtil.cifrarArchivo(tempZip, archivoSalida, password);
        tempZip.delete();
    }

    private static void exportarConfiguracionBotones(ZipOutputStream zos) throws IOException {
        File archivoProps = new File("botones_personalizados.properties");
        if (archivoProps.exists()) {
            ZipEntry entry = new ZipEntry("config/botones_personalizados.properties");
            zos.putNextEntry(entry);
            try (FileInputStream fis = new FileInputStream(archivoProps)) {
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesLeidos);
                }
            }
            zos.closeEntry();
            System.out.println("✅ Configuración de botones exportada");
        } else {
            System.out.println("ℹ️ No hay botones personalizados para exportar");
        }
    }

    private static JSONObject exportarEstructuraCompleta(Connection con) throws SQLException {
        JSONObject root = new JSONObject();
        root.put("version", "2.0");
        root.put("fecha", new java.util.Date().toString());

        // Usuarios
        JSONArray usuarios = new JSONArray();
        String sqlUsuarios = "SELECT id_usuario, username, ultimo_ingreso FROM usuarios";
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sqlUsuarios)) {
            while (rs.next()) {
                JSONObject u = new JSONObject();
                u.put("id", rs.getInt("id_usuario"));
                u.put("username", rs.getString("username"));
                u.put("ultimo_ingreso", rs.getString("ultimo_ingreso"));
                usuarios.put(u);
            }
        }
        root.put("usuarios", usuarios);

        // Periodos
        JSONArray periodos = new JSONArray();
        String sqlPeriodos = "SELECT * FROM periodos_academicos";
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sqlPeriodos)) {
            while (rs.next()) {
                JSONObject p = new JSONObject();
                p.put("id", rs.getInt("id_periodo"));
                p.put("codigo", rs.getString("codigo_periodo"));
                p.put("estado", rs.getString("estado"));
                periodos.put(p);
            }
        }
        root.put("periodos", periodos);

        // Plantillas personalizadas (ID > 4)
        JSONArray plantillas = new JSONArray();
        String sqlPlantillas = "SELECT * FROM plantillas WHERE id_plantilla > 4";
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sqlPlantillas)) {
            while (rs.next()) {
                JSONObject p = new JSONObject();
                int id = rs.getInt("id_plantilla");
                p.put("id", id);
                p.put("nombre", rs.getString("nombre_plantilla"));
                p.put("descripcion", rs.getString("descripcion"));
                p.put("fecha_creacion", rs.getString("fecha_creacion"));

                JSONArray campos = new JSONArray();
                String sqlCampos = "SELECT * FROM campos_plantilla WHERE id_plantilla = ?";
                try (PreparedStatement ps = con.prepareStatement(sqlCampos)) {
                    ps.setInt(1, id);
                    ResultSet rsCampos = ps.executeQuery();
                    while (rsCampos.next()) {
                        JSONObject c = new JSONObject();
                        c.put("id", rsCampos.getInt("id_campo"));
                        c.put("nombre", rsCampos.getString("nombre_columna"));
                        campos.put(c);
                    }
                }
                p.put("campos", campos);

                JSONArray docs = new JSONArray();
                String sqlDocs = "SELECT id_documento, nombre_archivo_origen, fecha_registro, id_periodo, nombre_archivo_original FROM documentos_planilla WHERE id_plantilla = ?";
                try (PreparedStatement ps = con.prepareStatement(sqlDocs)) {
                    ps.setInt(1, id);
                    ResultSet rsDocs = ps.executeQuery();
                    while (rsDocs.next()) {
                        JSONObject d = new JSONObject();
                        d.put("id", rsDocs.getInt("id_documento"));
                        d.put("nombre", rsDocs.getString("nombre_archivo_origen"));
                        d.put("fecha", rsDocs.getString("fecha_registro"));
                        d.put("id_periodo", rsDocs.getInt("id_periodo"));
                        d.put("nombre_original", rsDocs.getString("nombre_archivo_original"));
                        docs.put(d);
                    }
                }
                p.put("documentos", docs);

                plantillas.put(p);
            }
        }
        root.put("plantillas", plantillas);

        // ---------- Documentos de los botones predeterminados ----------
        JSONArray docsPredeterminados = new JSONArray();
        String sqlPred = """
            SELECT id_documento, id_plantilla, nombre_archivo_origen, fecha_registro,
                   id_periodo, nombre_archivo_original
            FROM documentos_planilla
            WHERE id_plantilla BETWEEN 1 AND 4
        """;
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sqlPred)) {
            while (rs.next()) {
                JSONObject d = new JSONObject();
                d.put("id", rs.getInt("id_documento"));
                d.put("id_plantilla", rs.getInt("id_plantilla"));
                d.put("nombre", rs.getString("nombre_archivo_origen"));
                d.put("fecha", rs.getString("fecha_registro"));
                d.put("id_periodo", rs.getInt("id_periodo"));
                d.put("nombre_original", rs.getString("nombre_archivo_original"));
                docsPredeterminados.put(d);
            }
        }
        root.put("documentos_predeterminados", docsPredeterminados);
        // -------------------------------------------------------------

        return root;
    }

    private static void exportarTodosLosDocumentos(Connection con, ZipOutputStream zos) throws SQLException, IOException {
        String sql = "SELECT id_documento, archivo_original FROM documentos_planilla WHERE archivo_original IS NOT NULL";
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int contador = 0;
            while (rs.next()) {
                int id = rs.getInt("id_documento");
                byte[] bytes = rs.getBytes("archivo_original");
                if (bytes != null && bytes.length > 0) {
                    ZipEntry zipEntry = new ZipEntry("documentos/doc_" + id + ".xlsx");
                    zos.putNextEntry(zipEntry);
                    zos.write(bytes);
                    zos.closeEntry();
                    contador++;
                }
            }
            System.out.println("📦 Documentos BLOB exportados: " + contador);
        }
    }

    private static void exportarDatosCeldas(Connection con, ZipOutputStream zos) throws SQLException, IOException {
        String sql = "SELECT id_documento, id_campo, numero_fila, valor_texto FROM valores_celda ORDER BY id_documento, numero_fila, id_campo";
        Map<Integer, List<Object[]>> datosPorDocumento = new HashMap<>();
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int idDoc = rs.getInt("id_documento");
                Object[] fila = new Object[]{
                    rs.getInt("id_campo"),
                    rs.getInt("numero_fila"),
                    rs.getString("valor_texto")
                };
                datosPorDocumento.computeIfAbsent(idDoc, k -> new ArrayList<>()).add(fila);
            }
        }
        for (Map.Entry<Integer, List<Object[]>> entry : datosPorDocumento.entrySet()) {
            int idDoc = entry.getKey();
            JSONArray celdas = new JSONArray();
            for (Object[] fila : entry.getValue()) {
                JSONObject celda = new JSONObject();
                celda.put("id_campo", (int) fila[0]);
                celda.put("numero_fila", (int) fila[1]);
                celda.put("valor_texto", (String) fila[2]);
                celdas.put(celda);
            }
            ZipEntry zipEntry = new ZipEntry("celdas/celdas_" + idDoc + ".json");
            zos.putNextEntry(zipEntry);
            zos.write(celdas.toString(2).getBytes("UTF-8"));
            zos.closeEntry();
        }
        System.out.println("📊 Datos de celdas exportados: " + datosPorDocumento.size() + " documentos");
    }

    // ==================== IMPORTAR ====================
    public static void importarRespaldo(Connection con, File archivoCifrado, String password) throws Exception {
        File tempZip = File.createTempFile("respaldo_temp_", ".zip");
        tempZip.deleteOnExit();

        try {
            CifradoUtil.descifrarArchivo(archivoCifrado, tempZip, password);
        } catch (Exception e) {
            throw new Exception("Contraseña incorrecta o archivo corrupto");
        }

        JSONObject estructura = null;
        Map<Integer, byte[]> documentos = new HashMap<>();
        Map<Integer, JSONArray> celdasPorDocumento = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(tempZip);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("estructura.json")) {
                    String json = new String(zis.readAllBytes(), "UTF-8");
                    estructura = new JSONObject(json);
                } else if (entry.getName().startsWith("documentos/")) {
                    byte[] bytes = zis.readAllBytes();
                    int id = Integer.parseInt(entry.getName().replaceAll("\\D+", ""));
                    documentos.put(id, bytes);
                } else if (entry.getName().startsWith("celdas/")) {
                    String json = new String(zis.readAllBytes(), "UTF-8");
                    int id = Integer.parseInt(entry.getName().replaceAll("\\D+", ""));
                    celdasPorDocumento.put(id, new JSONArray(json));
                }
                zis.closeEntry();
            }
        }

        if (estructura == null) {
            throw new Exception("Estructura no encontrada");
        }

        con.setAutoCommit(false);

        try {
            System.out.println("📥 Importando periodos...");
            importarPeriodosSeguro(con, estructura.getJSONArray("periodos"));

            System.out.println("📥 Importando usuarios...");
            importarUsuariosSeguro(con, estructura.getJSONArray("usuarios"));

            // 1. Importar documentos predeterminados (IDs 1-4)
            System.out.println("📥 Importando documentos de botones predeterminados...");
            importarDocumentosPredeterminados(con, estructura.getJSONArray("documentos_predeterminados"), documentos, celdasPorDocumento);

            // 2. Importar plantillas personalizadas (ID > 4)
            System.out.println("📥 Importando plantillas personalizadas...");
            Map<Integer, Integer> mapaIds = importarPlantillasSeguro(con, estructura.getJSONArray("plantillas"), documentos, celdasPorDocumento);

            // 3. Regenerar .properties
            if (!mapaIds.isEmpty()) {
                Properties props = new Properties();
                JSONArray plantillasJSON = estructura.getJSONArray("plantillas");
                int idx = 0;
                for (int i = 0; i < plantillasJSON.length(); i++) {
                    JSONObject p = plantillasJSON.getJSONObject(i);
                    int idOriginal = p.getInt("id");
                    int nuevoId = mapaIds.getOrDefault(idOriginal, -1);
                    if (nuevoId != -1) {
                        props.setProperty("boton_" + idx, p.getString("nombre"));
                        props.setProperty("boton_" + idx + ".id", String.valueOf(nuevoId));
                        idx++;
                    }
                }
                if (idx > 0) {
                    File archivoProps = new File("botones_personalizados.properties");
                    try (FileOutputStream fos = new FileOutputStream(archivoProps)) {
                        props.store(fos, "Botones personalizados generados al importar");
                    }
                    System.out.println("✅ Archivo .properties regenerado con " + idx + " botones personalizados");
                } else {
                    new File("botones_personalizados.properties").delete();
                }
            }

            con.commit();

            String resumen = String.format(
                "✅ Importación completada!\n\n" +
                "📊 Resumen:\n" +
                "• Documentos predeterminados: %d\n" +
                "• Plantillas personalizadas: %d nuevas\n\n" +
                "⚠️ Los datos existentes fueron sobrescritos si coincidían.",
                estructura.getJSONArray("documentos_predeterminados").length(),
                mapaIds.size()
            );
            JOptionPane.showMessageDialog(null, resumen, "Éxito", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
            tempZip.delete();
        }
    }

    // ---------- Importar documentos predeterminados (sobrescritura) ----------
    private static void importarDocumentosPredeterminados(Connection con, JSONArray docsPred,
                                                         Map<Integer, byte[]> documentos,
                                                         Map<Integer, JSONArray> celdasPorDocumento)
            throws SQLException {
        String sqlCheckDoc = "SELECT id_documento FROM documentos_planilla WHERE nombre_archivo_origen = ? AND id_plantilla = ?";
        String sqlUpdateDoc = """
            UPDATE documentos_planilla
            SET archivo_original = ?, nombre_archivo_original = ?, fecha_registro = ?
            WHERE id_documento = ?
        """;
        String sqlInsertDoc = """
            INSERT INTO documentos_planilla
            (id_plantilla, id_periodo, nombre_archivo_origen, archivo_original, nombre_archivo_original, fecha_registro)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        String sqlDeleteCeldas = "DELETE FROM valores_celda WHERE id_documento = ?";
        String sqlInsertCelda = "INSERT INTO valores_celda (id_documento, id_campo, numero_fila, valor_texto) VALUES (?, ?, ?, ?)";

        try (PreparedStatement psCheck = con.prepareStatement(sqlCheckDoc);
             PreparedStatement psUpdate = con.prepareStatement(sqlUpdateDoc);
             PreparedStatement psInsert = con.prepareStatement(sqlInsertDoc, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psDeleteCelda = con.prepareStatement(sqlDeleteCeldas);
             PreparedStatement psInsertCelda = con.prepareStatement(sqlInsertCelda)) {

            for (int i = 0; i < docsPred.length(); i++) {
                JSONObject d = docsPred.getJSONObject(i);
                int idPlantilla = d.getInt("id_plantilla"); // 1-4
                String nombre = d.getString("nombre");
                int idPeriodo = obtenerIdPeriodoPorCodigo(con, d.getInt("id_periodo"));
                int idOriginal = d.getInt("id");
                byte[] archivo = documentos.getOrDefault(idOriginal, null);
                String nombreOriginal = d.optString("nombre_original");
                String fecha = d.optString("fecha");

                // Verificar si ya existe un documento con el mismo nombre en esa plantilla
                psCheck.setString(1, nombre);
                psCheck.setInt(2, idPlantilla);
                ResultSet rs = psCheck.executeQuery();

                int idDocumentoExistente = -1;
                if (rs.next()) {
                    idDocumentoExistente = rs.getInt(1);
                }

                if (idDocumentoExistente != -1) {
                    // Sobrescribir documento existente
                    psUpdate.setBytes(1, archivo);
                    psUpdate.setString(2, nombreOriginal);
                    psUpdate.setString(3, fecha);
                    psUpdate.setInt(4, idDocumentoExistente);
                    psUpdate.executeUpdate();
                    System.out.println("🔄 Documento sobrescrito: " + nombre + " (ID " + idDocumentoExistente + ")");
                    // Eliminar celdas viejas
                    psDeleteCelda.setInt(1, idDocumentoExistente);
                    psDeleteCelda.executeUpdate();
                    // Insertar nuevas celdas
                    if (celdasPorDocumento.containsKey(idOriginal)) {
                        JSONArray celdas = celdasPorDocumento.get(idOriginal);
                        for (int k = 0; k < celdas.length(); k++) {
                            JSONObject celda = celdas.getJSONObject(k);
                            // Obtener el id_campo real de la plantilla predeterminada
                            int idCampoReal = obtenerIdCampoPorNombre(con, obtenerNombreCampoPorId(con, celda.getInt("id_campo")), idPlantilla);
                            if (idCampoReal != -1) {
                                psInsertCelda.setInt(1, idDocumentoExistente);
                                psInsertCelda.setInt(2, idCampoReal);
                                psInsertCelda.setInt(3, celda.getInt("numero_fila"));
                                psInsertCelda.setString(4, celda.getString("valor_texto"));
                                psInsertCelda.addBatch();
                            }
                        }
                        psInsertCelda.executeBatch();
                    }
                } else {
                    // Insertar nuevo documento
                    psInsert.setInt(1, idPlantilla);
                    psInsert.setInt(2, idPeriodo);
                    psInsert.setString(3, nombre);
                    psInsert.setBytes(4, archivo);
                    psInsert.setString(5, nombreOriginal);
                    psInsert.setString(6, fecha);
                    psInsert.executeUpdate();
                    ResultSet rsKey = psInsert.getGeneratedKeys();
                    int nuevoIdDoc = -1;
                    if (rsKey.next()) {
                        nuevoIdDoc = rsKey.getInt(1);
                    }
                    System.out.println("📄 Nuevo documento predeterminado: " + nombre + " (ID " + nuevoIdDoc + ")");
                    if (nuevoIdDoc != -1 && celdasPorDocumento.containsKey(idOriginal)) {
                        JSONArray celdas = celdasPorDocumento.get(idOriginal);
                        for (int k = 0; k < celdas.length(); k++) {
                            JSONObject celda = celdas.getJSONObject(k);
                            int idCampoReal = obtenerIdCampoPorNombre(con, obtenerNombreCampoPorId(con, celda.getInt("id_campo")), idPlantilla);
                            if (idCampoReal != -1) {
                                psInsertCelda.setInt(1, nuevoIdDoc);
                                psInsertCelda.setInt(2, idCampoReal);
                                psInsertCelda.setInt(3, celda.getInt("numero_fila"));
                                psInsertCelda.setString(4, celda.getString("valor_texto"));
                                psInsertCelda.addBatch();
                            }
                        }
                        psInsertCelda.executeBatch();
                    }
                }
            }
        }
    }

    // ---------- Importar plantillas personalizadas (con sobrescritura) ----------
    private static Map<Integer, Integer> importarPlantillasSeguro(Connection con, JSONArray plantillas,
                                                                  Map<Integer, byte[]> documentos,
                                                                  Map<Integer, JSONArray> celdasPorDocumento)
            throws SQLException {

        Map<Integer, Integer> mapaIds = new HashMap<>();

        String sqlCheckPlantilla = "SELECT id_plantilla FROM plantillas WHERE nombre_plantilla = ?";
        String sqlUpdatePlantilla = "UPDATE plantillas SET descripcion = ?, fecha_creacion = ? WHERE id_plantilla = ?";
        String sqlInsertPlantilla = "INSERT INTO plantillas (nombre_plantilla, descripcion, fecha_creacion) VALUES (?, ?, ?)";
        String sqlCheckCampo = "SELECT id_campo FROM campos_plantilla WHERE id_plantilla = ? AND nombre_columna = ?";
        String sqlInsertCampo = "INSERT OR IGNORE INTO campos_plantilla (id_plantilla, nombre_columna) VALUES (?, ?)";
        String sqlCheckDoc = "SELECT id_documento FROM documentos_planilla WHERE nombre_archivo_origen = ? AND id_plantilla = ?";
        String sqlUpdateDoc = """
            UPDATE documentos_planilla
            SET archivo_original = ?, nombre_archivo_original = ?, fecha_registro = ?
            WHERE id_documento = ?
        """;
        String sqlInsertDoc = """
            INSERT INTO documentos_planilla
            (id_plantilla, id_periodo, nombre_archivo_origen, archivo_original, nombre_archivo_original, fecha_registro)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        String sqlDeleteCeldas = "DELETE FROM valores_celda WHERE id_documento = ?";
        String sqlInsertCelda = "INSERT INTO valores_celda (id_documento, id_campo, numero_fila, valor_texto) VALUES (?, ?, ?, ?)";

        try (PreparedStatement psCheckP = con.prepareStatement(sqlCheckPlantilla);
             PreparedStatement psUpdateP = con.prepareStatement(sqlUpdatePlantilla);
             PreparedStatement psInsertP = con.prepareStatement(sqlInsertPlantilla, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psCheckC = con.prepareStatement(sqlCheckCampo);
             PreparedStatement psInsertC = con.prepareStatement(sqlInsertCampo, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psCheckD = con.prepareStatement(sqlCheckDoc);
             PreparedStatement psUpdateD = con.prepareStatement(sqlUpdateDoc);
             PreparedStatement psInsertD = con.prepareStatement(sqlInsertDoc, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psDeleteCeldas = con.prepareStatement(sqlDeleteCeldas);
             PreparedStatement psInsertCelda = con.prepareStatement(sqlInsertCelda)) {

            for (int i = 0; i < plantillas.length(); i++) {
                JSONObject p = plantillas.getJSONObject(i);
                String nombrePlantilla = p.getString("nombre");
                int idOriginal = p.getInt("id");
                int idPlantillaExistente = -1;

                psCheckP.setString(1, nombrePlantilla);
                ResultSet rsP = psCheckP.executeQuery();
                if (rsP.next()) {
                    idPlantillaExistente = rsP.getInt(1);
                }

                int nuevoIdPlantilla;
                if (idPlantillaExistente != -1) {
                    // Actualizar plantilla existente
                    nuevoIdPlantilla = idPlantillaExistente;
                    psUpdateP.setString(1, p.optString("descripcion"));
                    psUpdateP.setString(2, p.optString("fecha_creacion"));
                    psUpdateP.setInt(3, nuevoIdPlantilla);
                    psUpdateP.executeUpdate();
                    System.out.println("🔄 Plantilla personalizada actualizada: " + nombrePlantilla + " (ID " + nuevoIdPlantilla + ")");
                } else {
                    // Insertar nueva plantilla
                    psInsertP.setString(1, nombrePlantilla);
                    psInsertP.setString(2, p.optString("descripcion"));
                    psInsertP.setString(3, p.optString("fecha_creacion"));
                    psInsertP.executeUpdate();
                    ResultSet rs = psInsertP.getGeneratedKeys();
                    if (rs.next()) {
                        nuevoIdPlantilla = rs.getInt(1);
                        System.out.println("✅ Plantilla personalizada creada: " + nombrePlantilla + " (ID " + nuevoIdPlantilla + ")");
                    } else {
                        throw new SQLException("No se pudo obtener el ID de la plantilla");
                    }
                }
                mapaIds.put(idOriginal, nuevoIdPlantilla);

                // Campos: asegurar que existan
                JSONArray campos = p.getJSONArray("campos");
                for (int j = 0; j < campos.length(); j++) {
                    JSONObject c = campos.getJSONObject(j);
                    String nombreCampo = c.getString("nombre");
                    psCheckC.setInt(1, nuevoIdPlantilla);
                    psCheckC.setString(2, nombreCampo);
                    ResultSet rsC = psCheckC.executeQuery();
                    if (!rsC.next()) {
                        psInsertC.setInt(1, nuevoIdPlantilla);
                        psInsertC.setString(2, nombreCampo);
                        psInsertC.addBatch();
                    }
                }
                psInsertC.executeBatch();

                // Documentos
                JSONArray docs = p.getJSONArray("documentos");
                for (int j = 0; j < docs.length(); j++) {
                    JSONObject d = docs.getJSONObject(j);
                    String nombreDoc = d.getString("nombre");
                    int idDocOriginal = d.getInt("id");
                    int idPeriodo = obtenerIdPeriodoPorCodigo(con, d.getInt("id_periodo"));
                    byte[] archivo = documentos.getOrDefault(idDocOriginal, null);
                    String nombreOriginal = d.optString("nombre_original");
                    String fecha = d.optString("fecha");

                    // Verificar duplicado por nombre y plantilla
                    psCheckD.setString(1, nombreDoc);
                    psCheckD.setInt(2, nuevoIdPlantilla);
                    ResultSet rsD = psCheckD.executeQuery();

                    if (rsD.next()) {
                        // Sobrescribir documento existente
                        int idDocExistente = rsD.getInt(1);
                        psUpdateD.setBytes(1, archivo);
                        psUpdateD.setString(2, nombreOriginal);
                        psUpdateD.setString(3, fecha);
                        psUpdateD.setInt(4, idDocExistente);
                        psUpdateD.executeUpdate();
                        System.out.println("🔄 Documento sobrescrito: " + nombreDoc + " (ID " + idDocExistente + ")");
                        // Eliminar celdas viejas
                        psDeleteCeldas.setInt(1, idDocExistente);
                        psDeleteCeldas.executeUpdate();
                        // Insertar nuevas celdas
                        if (celdasPorDocumento.containsKey(idDocOriginal)) {
                            JSONArray celdas = celdasPorDocumento.get(idDocOriginal);
                            for (int k = 0; k < celdas.length(); k++) {
                                JSONObject celda = celdas.getJSONObject(k);
                                int idCampoOriginal = celda.getInt("id_campo");
                                String nombreCampo = obtenerNombreCampoPorId(con, idCampoOriginal);
                                if (nombreCampo != null) {
                                    int nuevoIdCampo = obtenerIdCampoPorNombre(con, nombreCampo, nuevoIdPlantilla);
                                    if (nuevoIdCampo != -1) {
                                        psInsertCelda.setInt(1, idDocExistente);
                                        psInsertCelda.setInt(2, nuevoIdCampo);
                                        psInsertCelda.setInt(3, celda.getInt("numero_fila"));
                                        psInsertCelda.setString(4, celda.getString("valor_texto"));
                                        psInsertCelda.addBatch();
                                    }
                                }
                            }
                            psInsertCelda.executeBatch();
                        }
                    } else {
                        // Insertar nuevo documento
                        psInsertD.setInt(1, nuevoIdPlantilla);
                        psInsertD.setInt(2, idPeriodo);
                        psInsertD.setString(3, nombreDoc);
                        psInsertD.setBytes(4, archivo);
                        psInsertD.setString(5, nombreOriginal);
                        psInsertD.setString(6, fecha);
                        psInsertD.executeUpdate();
                        ResultSet rsDoc = psInsertD.getGeneratedKeys();
                        int nuevoIdDoc = -1;
                        if (rsDoc.next()) {
                            nuevoIdDoc = rsDoc.getInt(1);
                        }
                        System.out.println("📄 Nuevo documento personalizado: " + nombreDoc + " (ID " + nuevoIdDoc + ")");
                        if (nuevoIdDoc != -1 && celdasPorDocumento.containsKey(idDocOriginal)) {
                            JSONArray celdas = celdasPorDocumento.get(idDocOriginal);
                            for (int k = 0; k < celdas.length(); k++) {
                                JSONObject celda = celdas.getJSONObject(k);
                                int idCampoOriginal = celda.getInt("id_campo");
                                String nombreCampo = obtenerNombreCampoPorId(con, idCampoOriginal);
                                if (nombreCampo != null) {
                                    int nuevoIdCampo = obtenerIdCampoPorNombre(con, nombreCampo, nuevoIdPlantilla);
                                    if (nuevoIdCampo != -1) {
                                        psInsertCelda.setInt(1, nuevoIdDoc);
                                        psInsertCelda.setInt(2, nuevoIdCampo);
                                        psInsertCelda.setInt(3, celda.getInt("numero_fila"));
                                        psInsertCelda.setString(4, celda.getString("valor_texto"));
                                        psInsertCelda.addBatch();
                                    }
                                }
                            }
                            psInsertCelda.executeBatch();
                        }
                    }
                }
            }
        }
        return mapaIds;
    }

    // ---------- Métodos auxiliares (sin cambios) ----------
    private static int importarPeriodosSeguro(Connection con, JSONArray periodos) throws SQLException {
        int contador = 0;
        String sqlCheck = "SELECT id_periodo FROM periodos_academicos WHERE codigo_periodo = ?";
        String sqlInsert = "INSERT OR IGNORE INTO periodos_academicos (codigo_periodo, estado) VALUES (?, ?)";
        try (PreparedStatement psCheck = con.prepareStatement(sqlCheck);
             PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
            for (int i = 0; i < periodos.length(); i++) {
                JSONObject p = periodos.getJSONObject(i);
                String codigo = p.getString("codigo");
                psCheck.setString(1, codigo);
                ResultSet rs = psCheck.executeQuery();
                if (!rs.next()) {
                    psInsert.setString(1, codigo);
                    psInsert.setString(2, p.optString("estado", "ACTIVO"));
                    psInsert.addBatch();
                    contador++;
                }
            }
            psInsert.executeBatch();
            System.out.println("✅ Periodos importados: " + contador);
        }
        return contador;
    }

    private static int importarUsuariosSeguro(Connection con, JSONArray usuarios) throws SQLException {
        int contador = 0;
        String sqlCheck = "SELECT id_usuario FROM usuarios WHERE username = ?";
        String sqlInsert = "INSERT OR IGNORE INTO usuarios (username, password, ultimo_ingreso) VALUES (?, ?, ?)";
        try (PreparedStatement psCheck = con.prepareStatement(sqlCheck);
             PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
            for (int i = 0; i < usuarios.length(); i++) {
                JSONObject u = usuarios.getJSONObject(i);
                String username = u.getString("username");
                psCheck.setString(1, username);
                ResultSet rs = psCheck.executeQuery();
                if (!rs.next()) {
                    psInsert.setString(1, username);
                    psInsert.setString(2, baseInicial.encriptarSHA256("admin123"));
                    psInsert.setString(3, u.optString("ultimo_ingreso"));
                    psInsert.addBatch();
                    contador++;
                }
            }
            psInsert.executeBatch();
        }
        return contador;
    }

    private static int obtenerIdPeriodoPorCodigo(Connection con, int idOriginal) throws SQLException {
        String sql = "SELECT codigo_periodo FROM periodos_academicos WHERE id_periodo = ?";
        String codigo = null;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idOriginal);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                codigo = rs.getString("codigo_periodo");
            }
        }
        if (codigo == null) return 1;
        sql = "SELECT id_periodo FROM periodos_academicos WHERE codigo_periodo = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_periodo");
            }
        }
        return 1;
    }

    private static int obtenerIdCampoPorNombre(Connection con, String nombreColumna, int idPlantilla) throws SQLException {
        String sql = "SELECT id_campo FROM campos_plantilla WHERE nombre_columna = ? AND id_plantilla = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreColumna);
            ps.setInt(2, idPlantilla);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_campo");
            }
        }
        return -1;
    }

    private static String obtenerNombreCampoPorId(Connection con, int idCampo) throws SQLException {
        String sql = "SELECT nombre_columna FROM campos_plantilla WHERE id_campo = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCampo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("nombre_columna");
            }
        }
        return null;
    }
} 