package server.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class EnhancedLogger {
    private static final String LOG_FILE = "server_logs.txt";
    private PrintWriter writer;
    private SimpleDateFormat dateFormat;

    // إحصائيات
    private Map<String, Integer> authCounts = new HashMap<>();
    private Map<String, Integer> sendCounts = new HashMap<>();
    private Map<String, Integer> listCounts = new HashMap<>();
    private Map<String, Integer> retrCounts = new HashMap<>();

    public EnhancedLogger() {
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            writer = new PrintWriter(new FileWriter(LOG_FILE, true));
            log("=".repeat(60));
            log("🚀 MailLite Server Started - " + new Date());
            log("=".repeat(60));
        } catch (IOException e) {
            System.err.println("❌ Failed to create log file: " + e.getMessage());
        }
    }

    public void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message;

        System.out.println(logEntry);

        if (writer != null) {
            writer.println(logEntry);
            writer.flush();
        }
    }

    // السجلات المطلوبة حسب المشروع
    public void logAuth(String username, boolean success, String ip) {
        String status = success ? "✅ SUCCESS" : "❌ FAILED";
        incrementCounter(authCounts, "AUTH_" + status);
        log("🔐 AUTH " + status + " - User: " + username + " | IP: " + ip);
    }

    public void logSend(String from, String to, String msgId, int size) {
        incrementCounter(sendCounts, from);
        log("📤 SEND - From: " + from + " | To: " + to +
                " | ID: " + msgId + " | Size: " + size + " bytes");
    }

    public void logList(String username, String folder, int count) {
        incrementCounter(listCounts, username);
        log("📋 LIST - User: " + username + " | Folder: " + folder +
                " | Retrieved: " + count + " messages");
    }

    public void logRetr(String username, String msgId) {
        incrementCounter(retrCounts, username);
        log("📥 RETR - User: " + username + " | MessageID: " + msgId);
    }

    public void logDele(String username, String msgId, boolean archive) {
        String action = archive ? "ARCHIVED" : "DELETED";
        log("🗑️  " + action + " - User: " + username + " | MessageID: " + msgId);
    }

    public void logRestore(String username, String msgId) {
        log("🔄 RESTORE - User: " + username + " | MessageID: " + msgId);
    }

    public void logRosterChange(String username, String oldStatus, String newStatus) {
        log("👥 ROSTER - User: " + username + " | Status: " +
                oldStatus + " → " + newStatus);
    }

    public void logUDP(String type, String details) {
        log("📡 UDP " + type + " - " + details);
    }

    public void logError(String operation, String error) {
        log("❌ ERROR - Operation: " + operation + " | Error: " + error);
    }

    public void logCleanup(int days, int count) {
        log("🧹 CLEANUP - Archived messages older than " + days +
                " days | Removed: " + count + " messages");
    }

    public void logUserManagement(String action, String username) {
        log("👤 USER " + action.toUpperCase() + " - Username: " + username);
    }

    private void incrementCounter(Map<String, Integer> counter, String key) {
        counter.put(key, counter.getOrDefault(key, 0) + 1);
    }

    public void printStatistics() {
        log("\n📊 ========== SERVER STATISTICS ==========");
        log("📊 Authentication attempts: " + authCounts);
        log("📊 Messages sent per user: " + sendCounts);
        log("📊 List operations per user: " + listCounts);
        log("📊 Retrieve operations per user: " + retrCounts);
        log("📊 =======================================\n");
    }

    public void close() {
        printStatistics();
        log("=".repeat(60));
        log("🛑 MailLite Server Stopped - " + new Date());
        log("=".repeat(60));
        if (writer != null) {
            writer.close();
        }
    }
}