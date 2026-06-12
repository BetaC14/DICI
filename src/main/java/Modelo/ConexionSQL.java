/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Modelo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
/**
 *
 * @author Pc
 */
public class ConexionSQL {
    // El archivo se creará en la misma carpeta donde se ejecute el programa
    private static final String URL = "jdbc:sqlite:dici_sistema.db";
    private static Connection conectar = null;

    public static Connection getConexion() {
        try {
            if (conectar == null || conectar.isClosed()) {
                // Esto crea el archivo .db automáticamente si no existe
                conectar = DriverManager.getConnection(URL);
                
                // Habilitamos las llaves foráneas en cada conexión
                try (Statement stmt = conectar.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
               
                //Esto llama a la estructura de la base de datos como tal
               baseInicial.esTruc(conectar);
            }
        } catch (SQLException e) {
            System.err.println("Error en la conexión: " + e.getMessage());
        }
        return conectar;
    }
}
