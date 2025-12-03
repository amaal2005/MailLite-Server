package server;

import server.gui.ServerGUI;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 Starting MailLite Server...");
        System.out.println("=".repeat(50));

        deleteOldSerializedFiles();

        javax.swing.SwingUtilities.invokeLater(() -> {
            ServerGUI serverGUI = new ServerGUI();
            serverGUI.setVisible(true);
        });
    }

    private static void deleteOldSerializedFiles() {
        String[] oldFiles = {
                "users.dat",
                "messages.dat",
                "data/users.dat",
                "data/messages.dat",
                "data/users.json",
                "users.json"
        };




        for (String filename : oldFiles) {
            File file = new File(filename);
            if (file.exists()) {
                if (file.delete()) {
                    System.out.println("🗑️ Deleted old file: " + filename);
                } else {
                    System.out.println("⚠️ Could not delete: " + filename);
                }
            }
        }
    }
}