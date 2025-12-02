package server.handlers;

import server.gui.ServerGUI;
import server.storage.UserManager;
import server.storage.MessageManager;
import server.storage.SessionManager;
import server.udp.UDPNotifier;
import server.utils.EnhancedLogger;
import server.models.UserSession;

import java.io.*;
import java.net.*;

public class EnhancedClientHandler implements Runnable {
    private final Socket clientSocket;
    private final BufferedReader in;
    private final PrintWriter out;
    private UserSession currentSession = null;

    private final UserManager userManager;
    private final MessageManager messageManager;
    private final SessionManager sessionManager;
    private final UDPNotifier udpNotifier;
    private final EnhancedLogger logger;
    private final ServerGUI gui;

    public EnhancedClientHandler(Socket socket, UserManager userManager,
                                 MessageManager messageManager, SessionManager sessionManager,
                                 UDPNotifier udpNotifier, EnhancedLogger logger,
                                 ServerGUI gui) throws IOException {
        this.clientSocket = socket;
        this.userManager = userManager;
        this.messageManager = messageManager;
        this.sessionManager = sessionManager;
        this.udpNotifier = udpNotifier;
        this.logger = logger;
        this.gui = gui;

        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    private void logToGUI(String msg) {
        if (gui != null) gui.log(msg);
    }

    @Override
    public void run() {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        logger.log("👤 Client handler started for IP: " + clientIP);

        try {
            String input;
            while ((input = in.readLine()) != null) {
                if (input.trim().isEmpty()) continue;

                logger.log("📨 Received: " + input + " from " +
                        (currentSession != null ? currentSession.getUsername() : "unknown"));

                processCommand(input.trim());

                if (input.equalsIgnoreCase("QUIT")) break;

                // تحديث آخر نشاط
                if (currentSession != null) {
                    currentSession.updateActivity();
                }
            }
        } catch (SocketTimeoutException e) {
            logger.log("⏰ Client timeout: " +
                    (currentSession != null ? currentSession.getUsername() : clientIP));
        } catch (Exception e) {
            logger.logError("Client Handler", e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void processCommand(String cmd) {
        try {
            String[] parts = cmd.split(" ", 2);
            String command = parts[0].toUpperCase();

            switch (command) {
                case "HELO" -> handleHelo(parts.length > 1 ? parts[1] : "");
                case "AUTH" -> handleAuth(parts.length > 1 ? parts[1] : "");
                case "LIST" -> { if (isAuthenticated()) handleList(cmd); }
                case "SEND" -> { if (isAuthenticated()) handleSend(); }
                case "RETR" -> { if (isAuthenticated()) handleRetr(parts.length > 1 ? parts[1] : ""); }
                case "DELE" -> { if (isAuthenticated()) handleDele(parts.length > 1 ? parts[1] : ""); }
                case "RESTORE" -> { if (isAuthenticated()) handleRestore(parts.length > 1 ? parts[1] : ""); }
                case "SETSTAT" -> { if (isAuthenticated()) handleSetStat(parts.length > 1 ? parts[1] : ""); }
                case "WHO" -> { if (isAuthenticated()) handleWho(); }
                case "STAT" -> { if (isAuthenticated()) handleStat(); }
                case "MARK" -> { if (isAuthenticated()) handleMark(parts.length > 1 ? parts[1] : ""); }
                case "EXPORT" -> { if (isAuthenticated()) handleExport(parts.length > 1 ? parts[1] : ""); }
                case "QUIT" -> handleQuit();
                default -> {
                    out.println("500 UNKNOWN COMMAND");
                    logger.log("❌ Unknown command: " + command);
                }
            }
        } catch (Exception e) {
            out.println("550 ERROR: " + e.getMessage());
            logger.logError("Process Command", e.getMessage());
        }
    }

    private void handleHelo(String params) {
        String[] p = params.split(" ");
        if (p.length == 0) {
            out.println("501 SYNTAX ERROR");
            return;
        }

        String username = p[0];
        int udpPort = -1;

        if (p.length > 1 && p[1].startsWith("UDP:")) {
            try {
                udpPort = Integer.parseInt(p[1].substring(4));
                logger.logUDP("REGISTER", username + " UDP:" + udpPort);
            } catch (Exception e) {
                logger.logError("UDP Port Parsing", e.getMessage());
            }
        }

        currentSession = sessionManager.createSession(username, clientSocket.getInetAddress(), udpPort);
        out.println("250 READY");

        logger.log("👋 HELO from " + username + " UDP:" + udpPort);
        logToGUI("Client connected: " + username);
    }

    private void handleAuth(String params) {
        if (currentSession == null) {
            out.println("503 HELO first");
            return;
        }

        String[] auth = params.split(" ", 2);
        if (auth.length < 2) {
            out.println("501 SYNTAX ERROR");
            return;
        }

        String username = auth[0];
        String password = auth[1];
        String ip = clientSocket.getInetAddress().getHostAddress();

        if (userManager.authenticateUser(username, password)) {
            currentSession.setAuthenticated(true);
            currentSession.setUsername(username);
            currentSession.setStatus("ACTIVE");

            sessionManager.updateUserStatus(username, "ACTIVE");
            userManager.updateUserStatus(username, "ACTIVE");

            out.println("235 AUTH SUCCESS");

            logger.logAuth(username, true, ip);
            logToGUI("✅ AUTH SUCCESS: " + username);

            // إرسال إشعار للمستخدمين الآخرين
            if (udpNotifier != null) {
                udpNotifier.broadcastStatus(username, "ACTIVE");
            }

        } else {
            out.println("535 AUTH FAILED");
            logger.logAuth(username, false, ip);
            logToGUI("❌ AUTH FAILED: " + username);
        }
    }

    private void handleSend() throws IOException {
        out.println("354 FROM? TO? SUBJ? BODYLEN?");
        String headers = in.readLine();
        if (headers == null) {
            out.println("550 TIMEOUT");
            return;
        }

        String from = "", to = "", subject = "";
        int bodyLen = 0;

        for (String part : headers.split(" ")) {
            if (part.startsWith("FROM:")) from = part.substring(5);
            else if (part.startsWith("TO:")) to = part.substring(3);
            else if (part.startsWith("SUBJ:")) subject = part.substring(5);
            else if (part.startsWith("BODYLEN:")) {
                try { bodyLen = Integer.parseInt(part.substring(8)); }
                catch (Exception e) { bodyLen = 0; }
            }
        }

        if (from.isEmpty() || to.isEmpty() || subject.isEmpty() || bodyLen <= 0) {
            out.println("550 INVALID HEADERS");
            return;
        }

        if (!from.equals(currentSession.getUsername())) {
            out.println("550 SENDER MISMATCH");
            return;
        }

        if (bodyLen > 64 * 1024) { // 64KB limit
            out.println("550 MESSAGE TOO LARGE");
            return;
        }

        out.println("354 SEND BODY");

        char[] bodyChars = new char[bodyLen];
        int totalRead = 0;
        while (totalRead < bodyLen) {
            int read = in.read(bodyChars, totalRead, bodyLen - totalRead);
            if (read == -1) break;
            totalRead += read;
        }

        in.readLine(); // Read the empty line after body

        String body = new String(bodyChars, 0, totalRead);
        String messageId = messageManager.saveMessage(from, to, subject, body);

        if (messageId != null) {
            out.println("250 MSGID " + messageId);

            logger.logSend(from, to, messageId, bodyLen);
            logToGUI("📤 Message sent: " + from + " -> " + to);

            // إرسال إشعارات UDP للمستلمين
            if (udpNotifier != null) {
                for (String recipient : to.split(",")) {
                    String rec = recipient.trim();
                    if (!rec.isEmpty()) {
                        int unread = messageManager.getUnreadCount(rec);
                        udpNotifier.sendNotification(rec, unread);
                    }
                }
            }
        } else {
            out.println("550 SAVE FAILED");
            logger.logError("Save Message", "Failed to save message from " + from);
        }
    }

    private void handleList(String cmd) {
        try {
            String folder = "INBOX";
            if (cmd.contains("ALL")) folder = "ALL";
            else if (cmd.contains("UNREAD")) folder = "UNREAD";
            else if (cmd.contains("SENT")) folder = "SENT";
            else if (cmd.contains("ARCHIVE")) folder = "ARCHIVE";

            String username = currentSession.getUsername();
            var messages = messageManager.listMessages(username, folder);

            logger.logList(username, folder, messages.size());

            out.println("213 " + messages.size());
            for (String m : messages) {
                out.println("213 " + m);
            }
            out.println("213 END");

            logToGUI("📋 LIST " + folder + " for " + username + ": " + messages.size() + " messages");

        } catch (Exception e) {
            out.println("550 ERROR IN LIST");
            logger.logError("LIST", e.getMessage());
        }
    }

    private void handleRetr(String params) {
        String messageId = params.trim();
        String username = currentSession.getUsername();

        String data = messageManager.getMessage(messageId, username);
        if (data != null) {
            String[] lines = data.split("\n");
            for (String line : lines) {
                out.println(line);
            }
            out.println("214 END");

            logger.logRetr(username, messageId);
            logToGUI("📥 RETR " + messageId + " for " + username);
        } else {
            out.println("550 MESSAGE NOT FOUND");
            logger.log("❌ RETR failed - Message not found: " + messageId);
        }
    }

    private void handleDele(String params) {
        String messageId = params.trim();
        String username = currentSession.getUsername();

        if (messageManager.archiveMessage(messageId, username)) {
            out.println("250 MESSAGE ARCHIVED");

            logger.logDele(username, messageId, true);
            logToGUI("🗑️ Archived: " + messageId + " for " + username);
        } else {
            out.println("550 ARCHIVE FAILED");
            logger.log("❌ Archive failed: " + messageId);
        }
    }

    private void handleRestore(String params) {
        String messageId = params.trim();
        String username = currentSession.getUsername();

        if (messageManager.restoreMessage(messageId, username)) {
            out.println("250 MESSAGE RESTORED");

            logger.logRestore(username, messageId);
            logToGUI("🔄 Restored: " + messageId + " for " + username);
        } else {
            out.println("550 RESTORE FAILED");
            logger.log("❌ Restore failed: " + messageId);
        }
    }

    private void handleMark(String params) {
        String messageId = params.trim();
        String username = currentSession.getUsername();

        if (messageManager.markAsRead(messageId, username)) {
            out.println("250 MESSAGE MARKED AS READ");
            logToGUI("📌 Marked as read: " + messageId);
        } else {
            out.println("550 MARK FAILED");
        }
    }

    private void handleSetStat(String params) {
        String status = params.toUpperCase().trim();
        String username = currentSession.getUsername();

        if ("ACTIVE,BUSY,AWAY".contains(status)) {
            String oldStatus = currentSession.getStatus();
            currentSession.setStatus(status);
            sessionManager.updateUserStatus(username, status);
            userManager.updateUserStatus(username, status);

            out.println("250 STATUS UPDATED");

            logger.logRosterChange(username, oldStatus, status);
            logToGUI("👤 Status update: " + username + " -> " + status);

            // إرسال إشعار للمستخدمين الآخرين
            if (udpNotifier != null) {
                udpNotifier.broadcastStatus(username, status);
            }
        } else {
            out.println("501 INVALID STATUS");
        }
    }

    private void handleWho() {
        try {
            var users = sessionManager.getOnlineUsers();

            out.println("212 " + users.size());
            for (String userInfo : users) {
                out.println("212U " + userInfo);
            }
            out.println("212 END");

            logger.log("👥 WHO request - " + users.size() + " online users");

        } catch (Exception e) {
            out.println("550 ERROR IN WHO");
            logger.logError("WHO", e.getMessage());
        }
    }

    private void handleStat() {
        try {
            String username = currentSession.getUsername();
            int unread = messageManager.getUnreadCount(username);
            int storage = messageManager.getStorageUsed(username);
            int online = sessionManager.getOnlineCount();

            String stats = "211 M:" + unread + " S:" + storage + " U:" + online;
            out.println(stats);

            logger.log("📊 STAT for " + username + " - Unread: " + unread +
                    ", Storage: " + storage + " bytes, Online: " + online);

        } catch (Exception e) {
            out.println("211 M:0 S:0 U:0");
            logger.logError("STAT", e.getMessage());
        }
    }

    private void handleExport(String params) {
        // هذا الأمر للعميل - السيرفر فقط يوافق
        out.println("250 EXPORT READY");
        logger.log("💾 EXPORT requested by " + currentSession.getUsername());
    }

    private void handleQuit() {
        out.println("221 BYE");
        logger.log("👋 QUIT from " + currentSession.getUsername());
        logToGUI("Client disconnected: " + currentSession.getUsername());
    }

    private boolean isAuthenticated() {
        if (currentSession == null || !currentSession.isAuthenticated()) {
            out.println("530 NOT AUTHENTICATED");
            return false;
        }
        return true;
    }

    private void disconnect() {
        try {
            if (currentSession != null && currentSession.isAuthenticated()) {
                String username = currentSession.getUsername();

                // تحديث حالة المستخدم
                sessionManager.removeSession(username);
                userManager.updateUserStatus(username, "OFFLINE");

                // إرسال إشعار للمستخدمين الآخرين
                if (udpNotifier != null) {
                    udpNotifier.broadcastStatus(username, "OFFLINE");
                }

                logger.log("🔌 Disconnected: " + username);
                logToGUI("Client disconnected: " + username);
            }

            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

        } catch (Exception e) {
            logger.logError("Disconnect", e.getMessage());
        }
    }
}