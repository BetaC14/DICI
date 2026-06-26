/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Modelo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Pc
 */
public class baseInicial {
    
    public static void esTruc(Connection con) {
        try (Statement stmt = con.createStatement()) {
            
            // 1.  Tabla Usuarios 
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL, -- Aquí se guardará el Hash SHA-256
                    ultimo_ingreso DATETIME
                );
            """);
            
            // 2. Tabla Periodos Académicos
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS periodos_academicos (
                    id_periodo INTEGER PRIMARY KEY AUTOINCREMENT,
                    codigo_periodo TEXT NOT NULL UNIQUE,
                    estado TEXT DEFAULT 'ACTIVO'
                );
            """);
            
            // 3. Tabla Plantillas
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS plantillas (
                    id_plantilla INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre_plantilla TEXT NOT NULL UNIQUE,
                    descripcion TEXT,
                    fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);
            
            // 4.  Tabla Campos Plantilla
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS campos_plantilla (
                    id_campo INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_plantilla INTEGER NOT NULL,
                    nombre_columna TEXT NOT NULL,
                    tipo_dato_interfaz TEXT DEFAULT 'TEXT',
                    FOREIGN KEY (id_plantilla) REFERENCES plantillas(id_plantilla) ON DELETE CASCADE,
                    UNIQUE(id_plantilla, nombre_columna)
                );
            """);
            
            // 5.  Tabla Documentos Planilla
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documentos_planilla (
                    id_documento INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_plantilla INTEGER NOT NULL,
                    id_periodo INTEGER NOT NULL,
                    nombre_archivo_origen TEXT,
                    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (id_plantilla) REFERENCES plantillas(id_plantilla) ON DELETE RESTRICT,
                    FOREIGN KEY (id_periodo) REFERENCES periodos_academicos(id_periodo) ON DELETE RESTRICT
                );
            """);

            // 6.  Tabla Valores Celda
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS valores_celda (
                    id_valor INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_documento INTEGER NOT NULL,
                    id_campo INTEGER NOT NULL,
                    numero_fila INTEGER NOT NULL,
                    valor_texto TEXT,
                    FOREIGN KEY (id_documento) REFERENCES documentos_planilla(id_documento) ON DELETE CASCADE,
                    FOREIGN KEY (id_campo) REFERENCES campos_plantilla(id_campo) ON DELETE RESTRICT,
                    UNIQUE(id_documento, id_campo, numero_fila)
                );
            """);
            
            // 7.  Índice de Optimización
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_valores_busqueda ON valores_celda(id_documento, numero_fila);
            """);
            
            // --- INSERCIÓN DE DATOS INICIALES (como referencia y por defecto) ---
            
            // Creamos el periodo por defecto
            stmt.execute("""
                INSERT OR IGNORE INTO periodos_academicos (id_periodo, codigo_periodo) 
                VALUES (1, '2025-2026');
            """);

            // Encriptamos la clave 'admin123' antes de guardarla por primera vez
            String claveEncriptada = encriptarSHA256("admin123");
            
            String sqlUsuario = """
                INSERT OR IGNORE INTO usuarios (id_usuario, username, password) 
                VALUES (1, 'director', ?);
            """;
             
String sqlPlantillasIniciales = """
    INSERT OR IGNORE INTO plantillas (id_plantilla, nombre_plantilla, descripcion) 
    VALUES 
    (1, 'DATOS_INST', 'Datos Institucionales'),
    (2, 'MATRICULA', 'Matrícula Estudiantil'),
    (3, 'SALUD', 'Salud y Bienes'),
    (4, 'NOMINA', 'Nómina RAC');
""";

try (Statement stmtPlantillas = con.createStatement()) {
    stmtPlantillas.execute(sqlPlantillasIniciales);
    System.out.println("Secciones de DICI vinculadas a la base de datos.");
}
            try (PreparedStatement pstmt = con.prepareStatement(sqlUsuario)) {
                pstmt.setString(1, claveEncriptada);
                pstmt.executeUpdate();
            }
            
            System.out.println("Base de datos verificada e inicializada con seguridad SHA-256.");

        } catch (SQLException e) {
            System.err.println("Error crítico al inicializar las tablas: " + e.getMessage());
        }
    }

    // Método auxiliar para encriptar en formato SHA-256 (Nativo de Java)
    public static String encriptarSHA256(String passwordOriginal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(passwordOriginal.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString(); // Retorna el texto largo y seguro de 64 caracteres
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error al encriptar: " + e.getMessage());
            return passwordOriginal;
        }
    }
}

            





       
        
            
           
    
         
            
          
        
    

