package com.mycompany.dc;

import com.formdev.flatlaf.themes.FlatMacLightLaf;

public class DC {
    public static void main(String[] args) {
        FlatMacLightLaf.setup();
        java.awt.EventQueue.invokeLater(() -> {
            new Login().setVisible(true);
        });
    }
}