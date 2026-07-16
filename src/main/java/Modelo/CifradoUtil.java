package Modelo;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.SecureRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CifradoUtil {
    
    private static final String ALGORITMO = "AES";
    private static final String TRANSFORMACION = "AES/CBC/PKCS5Padding";
    private static final int ITERACIONES = 10000;
    private static final int LONGITUD_CLAVE = 256;
    
    // Generar clave a partir de la contraseña
    private static SecretKey generarClave(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERACIONES, LONGITUD_CLAVE);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, ALGORITMO);
    }
    
    // Cifrar archivo ZIP
    public static void cifrarArchivo(File archivoEntrada, File archivoSalida, String password) throws Exception {
        // Generar salt aleatorio
        byte[] salt = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        
        // Generar IV (vector de inicialización)
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        
        SecretKey key = generarClave(password, salt);
        Cipher cipher = Cipher.getInstance(TRANSFORMACION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.IvParameterSpec(iv));
        
        try (FileInputStream fis = new FileInputStream(archivoEntrada);
             FileOutputStream fos = new FileOutputStream(archivoSalida);
             CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
            
            // Escribir salt + IV al inicio del archivo (necesario para descifrar)
            fos.write(salt);
            fos.write(iv);
            
            // Cifrar y escribir el contenido
            byte[] buffer = new byte[4096];
            int bytesLeidos;
            while ((bytesLeidos = fis.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesLeidos);
            }
            cos.flush();
        }
    }
    
    // Descifrar archivo cifrado
    public static void descifrarArchivo(File archivoEntrada, File archivoSalida, String password) throws Exception {
        try (FileInputStream fis = new FileInputStream(archivoEntrada)) {
            // Leer salt (16 bytes)
            byte[] salt = new byte[16];
            if (fis.read(salt) != 16) {
                throw new Exception("Formato de archivo inválido");
            }
            
            // Leer IV (16 bytes)
            byte[] iv = new byte[16];
            if (fis.read(iv) != 16) {
                throw new Exception("Formato de archivo inválido");
            }
            
            SecretKey key = generarClave(password, salt);
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, key, new javax.crypto.spec.IvParameterSpec(iv));
            
            try (CipherInputStream cis = new CipherInputStream(fis, cipher);
                 FileOutputStream fos = new FileOutputStream(archivoSalida)) {
                
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesLeidos);
                }
                fos.flush();
            }
        }
    }
}