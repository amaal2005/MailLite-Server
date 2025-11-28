// server/Main.java (معدل)
package server;

import server.gui.ServerGUI;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 Starting MailLite Server...");
        System.out.println("📧 An-Najah National University - Networks 1 Project");
        System.out.println("==========================================");

        // بدء الواجهة الرسومية
        javax.swing.SwingUtilities.invokeLater(() -> {
            ServerGUI serverGUI = new ServerGUI();
            serverGUI.setVisible(true);
        });
    }
}