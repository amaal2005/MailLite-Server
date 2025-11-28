package server.handlers;

import server.gui.ServerGUI;
import server.storage.UserManager;
import server.storage.MessageManager;
import server.storage.SessionManager;
import server.models.UserSession;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final BufferedReader in;
    private final PrintWriter out;
    private UserSession currentSession = null;

    private final UserManager userManager;
    private final MessageManager messageManager;
    private final SessionManager sessionManager;
    private final ServerGUI gui;

    public ClientHandler(Socket socket, UserManager userManager,
                         MessageManager messageManager, SessionManager sessionManager,
                         ServerGUI gui) throws IOException {
        this.clientSocket = socket;
        this.userManager = userManager;
        this.messageManager = messageManager;
        this.sessionManager = sessionManager;
        this.gui = gui;

        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    private void log(String msg) {
        if (gui != null) gui.log(msg);
        System.out.println("[Client " + clientSocket.getInetAddress() + "] " + msg);
    }

    @Override
    public void run() {
        try {
            String input;
            while ((input = in.readLine()) != null) {
                if (input.trim().isEmpty()) continue;
                log("← " + input);
                processCommand(input.trim());
                if (input.equalsIgnoreCase("QUIT")) break;
            }
        } catch (Exception e) {
            log("Client error: " + e.getMessage());
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
                case "QUIT" -> handleQuit();
                default -> out.println("500 UNKNOWN COMMAND");
            }
        } catch (Exception e) {
            out.println("550 ERROR: " + e.getMessage());
        }
    }

    private void handleHelo(String params) {
        String[] p = params.split(" ");
        String username = p[0];
        int udpPort = -1;
        if (p.length > 1 && p[1].startsWith("UDP:")) {
            try { udpPort = Integer.parseInt(p[1].substring(4)); } catch (Exception ignored) {}
        }

        currentSession = sessionManager.createSession(username, clientSocket.getInetAddress(), udpPort);
        out.println("250 READY");
        log("HELO from " + username + " (UDP:" + udpPort + ")");
    }

    private void handleAuth(String params) {
        if (currentSession == null) { out.println("503 HELO first"); return; }
        String[] auth = params.split(" ", 2);
        if (auth.length < 2) { out.println("501 SYNTAX ERROR"); return; }

        String username = auth[0];
        String password = auth[1];

        if (userManager.authenticateUser(username, password)) {
            currentSession.setAuthenticated(true);
            currentSession.setUsername(username);
            userManager.updateUserStatus(username, "ACTIVE");
            out.println("235 AUTH SUCCESS");
            log("AUTH SUCCESS: " + username);
        } else {
            out.println("535 AUTH FAILED");
            log("AUTH FAILED: " + username);
        }
    }

    private void handleSend() throws IOException {
        out.println("354 FROM? TO? SUBJ? BODYLEN?");
        String headers = in.readLine();
        if (headers == null) { out.println("550 TIMEOUT"); return; }

        String from = "", to = "", subject = "";
        int bodyLen = 0;

        for (String part : headers.split(" ")) {
            if (part.startsWith("FROM:")) from = part.substring(5);
            if (part.startsWith("TO:")) to = part.substring(3);
            if (part.startsWith("SUBJ:")) subject = part.substring(5);
            if (part.startsWith("BODYLEN:")) {
                try { bodyLen = Integer.parseInt(part.substring(8)); } catch (Exception e) { bodyLen = 0; }
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

        out.println("354 SEND BODY");

        char[] bodyChars = new char[bodyLen];
        int totalRead = 0;
        while (totalRead < bodyLen) {
            int read = in.read(bodyChars, totalRead, bodyLen - totalRead);
            if (read == -1) break;
            totalRead += read;
        }

        in.readLine();

        String body = new String(bodyChars, 0, totalRead);

        String messageId = messageManager.saveMessage(from, to, subject, body);
        if (messageId != null) {
            out.println("250 MSGID " + messageId);
            sendUDPNotifications(to);
            log("Message sent successfully → " + to + " | ID: " + messageId);
        } else {
            out.println("550 SAVE FAILED");
        }
    }

    private void sendUDPNotifications(String recipients) {
        for (String rec : recipients.split(",")) {
            String r = rec.trim();
            if (r.isEmpty()) continue;

            UserSession s = sessionManager.getSession(r);
            if (s != null && s.getUdpPort() > 0) {
                int unread = messageManager.getUnreadCount(r);
                String notif = "NOTIFY NEWMAIL " + r + " " + unread;

                try (DatagramSocket ds = new DatagramSocket()) {
                    byte[] buf = notif.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, s.getIpAddress(), s.getUdpPort());
                    ds.send(packet);
                    log("UDP Notification → " + r + " (unread: " + unread + ")");
                } catch (Exception ignored) {}
            }
        }
    }

    private void handleList(String cmd) {
        try {
            String folder = "INBOX";
            if (cmd.contains("SENT")) folder = "SENT";
            else if (cmd.contains("ARCHIVE")) folder = "ARCHIVE";
            else if (cmd.contains("UNREAD")) folder = "UNREAD";

            log("Listing messages for folder: " + folder + " - User: " + currentSession.getUsername());

            var messages = messageManager.listMessages(currentSession.getUsername(), folder);
            log("Found " + messages.size() + " messages in " + folder);

            out.println("213 " + messages.size());
            for (String m : messages) {
                out.println("213 " + m);
            }
            out.println("213 END");

        } catch (Exception e) {
            log("Error in LIST: " + e.getMessage());
            out.println("550 ERROR IN LIST");
        }
    }

    private void handleRetr(String params) {
        String messageId = params.trim();
        log("RETR request for: " + messageId + " - User: " + currentSession.getUsername());

        String data = messageManager.getMessage(messageId, currentSession.getUsername());
        if (data != null) {
            String[] lines = data.split("\n");
            for (String line : lines) {
                out.println(line);
            }
            out.println("214 END");
            log("RETR successful for: " + messageId);
        } else {
            out.println("550 MESSAGE NOT FOUND");
            log("RETR failed - message not found: " + messageId);
        }
    }

    private void handleDele(String params) {
        String messageId = params.trim();
        log("DELE request for: " + messageId + " - User: " + currentSession.getUsername());

        if (messageManager.archiveMessage(messageId, currentSession.getUsername())) {
            out.println("250 MESSAGE ARCHIVED");
            log("Message archived successfully: " + messageId);
        } else {
            out.println("550 ARCHIVE FAILED");
            log("Archive failed for: " + messageId);
        }
    }

    private void handleRestore(String params) {
        String messageId = params.trim();
        log("RESTORE request for: " + messageId + " - User: " + currentSession.getUsername());

        if (messageManager.restoreMessage(messageId, currentSession.getUsername())) {
            out.println("250 MESSAGE RESTORED");
            log("Message restored successfully: " + messageId);
        } else {
            out.println("550 RESTORE FAILED");
            log("Restore failed for: " + messageId);
        }
    }

    private void handleMark(String params) {
        String messageId = params.trim();
        log("MARK as read request for: " + messageId + " - User: " + currentSession.getUsername());

        if (messageManager.markAsRead(messageId, currentSession.getUsername())) {
            out.println("250 MESSAGE MARKED AS READ");
            log("Message marked as read: " + messageId);
        } else {
            out.println("550 MARK FAILED");
            log("Mark as read failed for: " + messageId);
        }
    }

    private void handleSetStat(String params) {
        String status = params.toUpperCase();
        if ("ACTIVE,BUSY,AWAY".contains(status)) {
            sessionManager.updateUserStatus(currentSession.getUsername(), status);
            userManager.updateUserStatus(currentSession.getUsername(), status);
            out.println("250 STATUS UPDATED");
            log("Status updated to: " + status + " for user: " + currentSession.getUsername());
        } else {
            out.println("501 INVALID STATUS");
            log("Invalid status attempt: " + status);
        }
    }

    private void handleWho() {
        try {
            var users = sessionManager.getOnlineUsers();
            log("WHO request - Found " + users.size() + " online users");

            out.println("212 " + users.size());
            for (String userInfo : users) {
                out.println("212U " + userInfo);
            }
            out.println("212 END");

        } catch (Exception e) {
            log("Error in WHO: " + e.getMessage());
            out.println("550 ERROR IN WHO");
        }
    }

    private void handleStat() {
        try {
            int unread = messageManager.getUnreadCount(currentSession.getUsername());
            int storage = messageManager.getStorageUsed(currentSession.getUsername());
            int online = sessionManager.getOnlineCount();

            String stats = "211 M:" + unread + " S:" + storage + " U:" + online;
            out.println(stats);
            log("STAT request - " + stats);

        } catch (Exception e) {
            log("Error in STAT: " + e.getMessage());
            out.println("211 M:0 S:0 U:0");
        }
    }

    private void handleQuit() {
        out.println("221 BYE");
        log("Client quit: " + currentSession.getUsername());
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
                sessionManager.removeSession(username);
                userManager.updateUserStatus(username, "OFFLINE");
                log("Disconnected and cleaned up session for: " + username);
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (Exception e) {
            log("Error during disconnect: " + e.getMessage());
        }
    }
}