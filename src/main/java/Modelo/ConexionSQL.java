/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Modelo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
/**
 *
 * @author Pc
 */
public class ConexionSQL {
    private static final String URL = "jdbc:sqlite:dici_sistema.db";
    private static Connection conectar = null;
    private static boolean baseVerificada = false; // ← NUEVO: para no verificar cada vez

    public static Connection getConexion() {
        try {
            if (conectar == null || conectar.isClosed()) {
                conectar = DriverManager.getConnection(URL);
                
                try (Statement stmt = conectar.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
                
                // 🔥 SOLO VERIFICAR UNA VEZ
                if (!baseVerificada) {
                    baseVerificada = true;
                    
                    // Verificar si la tabla 'usuarios' existe
                    boolean tablaExiste = false;
                    try (Statement stmt = conectar.createStatement();
                         ResultSet rs = stmt.executeQuery(
                             "SELECT name FROM sqlite_master WHERE type='table' AND name='usuarios';"
                         )) {
                        if (rs.next()) {
                            tablaExiste = true;
                        }
                    }
                    
                    // Solo ejecutar esTruc() si la tabla NO existe
                    if (!tablaExiste) {
                        System.out.println("🆕 Base de datos nueva. Creando estructura...");
                        baseInicial.esTruc(conectar);
                    } else {
                        System.out.println("✅ Base de datos existente. Usando datos actuales.");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error en la conexión: " + e.getMessage());
        }
        return conectar;
    }
}