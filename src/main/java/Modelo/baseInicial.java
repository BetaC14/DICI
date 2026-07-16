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
import java.sql.ResultSet;

/**
 * @author Pc / Ana
 */
public class baseInicial {
  
  public static void esTruc(Connection con) {
      
    try (Statement stmt = con.createStatement()) {
        
        // 🔥 VERIFICACIÓN DE SEGURIDAD
        try {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM usuarios;");
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("⚠️ La base de datos ya tiene usuarios.");
                System.out.println("   NO se recreará la estructura (datos seguros).");
                return; // ← SALIR SEGURO
            }
        } catch (SQLException e) {
            System.out.println("ℹ️ Tabla 'usuarios' no existe. Creando estructura...");
        }
        
        System.out.println("🔄 RECREANDO BASE DE DATOS CON SOPORTE BLOB...");
       
        // 1. ELIMINAR TABLAS EXISTENTES (EN ORDEN CORRECTO)
       
        try {
            // Primero las tablas con FOREIGN KEYS
            stmt.execute("DROP TABLE IF EXISTS valores_celda;");
            stmt.execute("DROP TABLE IF EXISTS documentos_planilla;");
            stmt.execute("DROP TABLE IF EXISTS campos_plantilla;");
            stmt.execute("DROP TABLE IF EXISTS plantillas;");
            stmt.execute("DROP TABLE IF EXISTS periodos_academicos;");
            stmt.execute("DROP TABLE IF EXISTS usuarios;");
            System.out.println("🗑️ Tablas antiguas eliminadas");
        } catch (SQLException e) {
            System.out.println("⚠️ No se pudieron eliminar todas las tablas: " + e.getMessage());
        }
        
        // 2. CREAR TABLAS 
        // 1. Tabla Usuarios
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS usuarios (
                id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL,
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
        
        // 4. Tabla Campos Plantilla
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
        
        // 5. Tabla Documentos Planilla (CON SOPORTE BLOB)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS documentos_planilla (
                id_documento INTEGER PRIMARY KEY AUTOINCREMENT,
                id_plantilla INTEGER NOT NULL,
                id_periodo INTEGER NOT NULL,
                nombre_archivo_origen TEXT,
                fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP,
                archivo_original BLOB,              -- ✅ Guarda el archivo Excel completo
                nombre_archivo_original TEXT,       -- ✅ Nombre original del archivo
                FOREIGN KEY (id_plantilla) REFERENCES plantillas(id_plantilla) ON DELETE RESTRICT,
                FOREIGN KEY (id_periodo) REFERENCES periodos_academicos(id_periodo) ON DELETE RESTRICT
            );
        """);

        // 6. Tabla Valores Celda
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
        
        // 7. Índice de Optimización
        stmt.execute("""
            CREATE INDEX IF NOT EXISTS idx_valores_busqueda ON valores_celda(id_documento, numero_fila);
        """);
        
        System.out.println("✅ Tablas creadas con soporte para archivos BLOB");
        
      
        // 3. INSERTAR DATOS INICIALES
       
        
        // Periodo inicial por defecto
        stmt.execute("""
            INSERT OR IGNORE INTO periodos_academicos (id_periodo, codigo_periodo) 
            VALUES (1, '2025-2026');
        """);

        // Usuario Administrador
        String claveEncriptada = encriptarSHA256("admin123");
        String sqlUsuario = """
            INSERT OR IGNORE INTO usuarios (id_usuario, username, password) 
            VALUES (1, 'director', ?);
        """;
        try (PreparedStatement pstmt = con.prepareStatement(sqlUsuario)) {
            pstmt.setString(1, claveEncriptada);
            pstmt.executeUpdate();
        }
         
        // Las 4 Secciones principales / Plantillas
        String sqlPlantillasIniciales = """
            INSERT OR IGNORE INTO plantillas (id_plantilla, nombre_plantilla, descripcion) 
            VALUES 
            (1, 'DATOS_INST', 'Datos Institucionales'),
            (2, 'MATRICULA', 'Matrícula Estudiantil'),
            (3, 'SALUD', 'Salud y Bienes'),
            (4, 'NOMINA', 'Nómina RAC');
        """;
        stmt.execute(sqlPlantillasIniciales);
        
        System.out.println("✅ Datos iniciales insertados correctamente");
        System.out.println("✅ Base de datos recreada con éxito con soporte BLOB");

    } catch (SQLException e) {
        System.err.println("❌ Error crítico al recrear las tablas: " + e.getMessage());
        e.printStackTrace();
    }
}

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
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error al encriptar: " + e.getMessage());
            return passwordOriginal;
        }
    }
}



       
        
            
           
    
         
            
          
        
    

