package configuracion;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.net.URL;

public class VentanaInfoFlotante extends JDialog {

    // Ubicación de la imagen dentro de la carpeta de recursos del proyecto
    private final String RUTA_LOGO = "/Dici.png"; 

    public VentanaInfoFlotante(JFrame padre) {
        // Configuramos el diálogo para que se apoye en la ventana principal
        super(padre, "Información del Sistema", true);
        setSize(450, 400); 
        setLocationRelativeTo(padre);
        setLayout(new BorderLayout());

        // Construimos el botón que va a contener toda la animación del logo
        JButton btnLogo = new JButton() {
            private Image image;
            // Variables de control para los ciclos trigonométricos del movimiento
            private double tiempoX = 0, tiempoY = 0, tiempoRotacion = 0;
            private Timer timer;

            // Inicializador del botón
            {
                try {
                    URL imgURL = getClass().getResource(RUTA_LOGO);
                    if (imgURL != null) {
                        // Toolkit carga la imagen de forma nativa y más ligera para el sistema
                        image = Toolkit.getDefaultToolkit().getImage(imgURL);
                    }
                } catch (Exception e) {
                    System.out.println("Revisa la ruta, no encontré el logo.");
                }

                // Ajustes estéticos para limpiar el botón y dejar solo el logo visible
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                // Hilo de animación ajustado a 16ms (~60 FPS) para que vaya suave
                timer = new Timer(16, e -> {
                    // El secreto del vaivén de FL Studio: avanzar los ejes a distintas velocidades
                    tiempoX += 0.03;
                    tiempoY += 0.02;
                    tiempoRotacion += 0.015;
                    repaint(); // Forzamos el redibujado continuo
                });
                timer.start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (image == null) return;

                Graphics2D g2d = (Graphics2D) g.create();
                
                // OPTIMIZACIÓN CLAVE: Filtros de renderizado de alto rendimiento para evitar el lag
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                // Buscamos el punto medio exacto del botón para posicionar el logo
                int centerX = getWidth() / 2;
                int centerY = getHeight() / 2;

                // Calculamos las ondas usando seno y coseno para lograr el efecto flotante orgánico
                int offsetX = (int) (Math.sin(tiempoX) * 7);
                int offsetY = (int) (Math.cos(tiempoY) * 10);
                double angulo = Math.sin(tiempoRotacion) * 0.06; // Rotación sutil

                // Aplicamos las coordenadas y el ángulo al lienzo gráfico antes de dibujar
                AffineTransform tx = new AffineTransform();
                // CORREGIDO: Se le suman 25 píxeles a en Y para bajar el logo un poco más hacia el título
                tx.translate(centerX + offsetX, centerY + offsetY + 25);
                tx.rotate(angulo);
                // Desplazamos la mitad del tamaño del logo para que rote sobre su propio centro
                tx.translate(-image.getWidth(null) / 2.0, -image.getHeight(null) / 2.0);

                g2d.drawImage(image, tx, null);
                g2d.dispose(); // Liberamos los recursos gráficos en cada frame
                
                // ULTRA OPTIMIZACIÓN: Sincroniza el búfer gráfico con el sistema operativo
                Toolkit.getDefaultToolkit().sync();
            }
        };

        // Espacio que va a ocupar el botón dentro de la ventana
        btnLogo.setPreferredSize(new Dimension(220, 220));

        // Evento que se dispara al hacerle clic al logo flotante
        btnLogo.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, 
                "Proyecto hecho con amor por estudiantes Unefistas", 
                "DICI", 
                JOptionPane.INFORMATION_MESSAGE);
        });

        // Panel inferior para organizar las etiquetas de texto de la sección Info
        JPanel panelTexto = new JPanel();
        panelTexto.setLayout(new BoxLayout(panelTexto, BoxLayout.Y_AXIS));
        panelTexto.setBorder(BorderFactory.createEmptyBorder(10, 20, 25, 20));

        // Modificado: Ahora solo dice DICI
        JLabel lblTitulo = new JLabel("DICI");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblVersion = new JLabel("Versión 1.0.0");
        lblVersion.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblVersion.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Reincorporado: Detecta dinámicamente si es Windows, Linux, etc.
        JLabel lblOS = new JLabel("Sistema: " + System.getProperty("os.name"));
        lblOS.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblOS.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Agregamos los textos al panel inferior con pequeños separadores invisibles
        panelTexto.add(lblTitulo);
        panelTexto.add(Box.createVerticalStrut(6));
        panelTexto.add(lblVersion);
        panelTexto.add(Box.createVerticalStrut(6));
        panelTexto.add(lblOS);

        // Añadimos todo a los bordes del diálogo principal
        add(btnLogo, BorderLayout.CENTER);
        add(panelTexto, BorderLayout.SOUTH);
    }
}