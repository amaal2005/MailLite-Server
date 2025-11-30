package server;

import server.gui.ServerGUI;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 Starting MailLite Server...");
        System.out.println("==========================================");

        javax.swing.SwingUtilities.invokeLater(() -> {
            ServerGUI serverGUI = new ServerGUI();
            serverGUI.setVisible(true);
        });
    }
}