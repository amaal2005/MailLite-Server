package server;

import server.gui.ServerGUI;
import server.handlers.EnhancedClientHandler;
import server.storage.UserManager;
import server.storage.MessageManager;
import server.storage.SessionManager;
import server.udp.UDPNotifier;
import server.utils.EnhancedLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MailServer {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private ScheduledExecutorService maintenanceScheduler;
    private UDPNotifier udpNotifier;
    private boolean running;
    private int port;
    private EnhancedLogger logger;
    private int cleanupDays = 30;
    private int udpPort = 10000;

    private UserManager userManager;
    private MessageManager messageManager;
    private SessionManager sessionManager;
    private ServerGUI serverGUI;

    public MailServer(int port, ServerGUI gui) {
        this.port = port;
        this.serverGUI = gui;
        this.logger = new EnhancedLogger();

        initializeComponents();

        logger.log("✅ MailServer initialized");
        logger.log("📝 Port: " + port + " | UDP: " + udpPort + " | Cleanup: " + cleanupDays + " days");
    }

    private void initializeComponents() {
        this.userManager = new UserManager();
        this.messageManager = new MessageManager();
        this.sessionManager = new SessionManager();

        this.threadPool = Executors.newCachedThreadPool();
        this.maintenanceScheduler = Executors.newScheduledThreadPool(3);
        this.running = false;
    }

    public void start() {
        if (running) {
            logger.log("⚠️ Server is already running!");
            return;
        }

        try {
            startUDPNotifier();

            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;

            logger.log("🚀 Server started successfully on port " + port);
            serverGUI.log("✅ Server started on port " + port);

            startMaintenanceTasks();

            acceptClients();

        } catch (IOException e) {
            logger.logError("Startup", e.getMessage());
            serverGUI.log("❌ Failed to start server: " + e.getMessage());
            stop();
        }
    }

    private void startUDPNotifier() {
        udpNotifier = new UDPNotifier(udpPort, sessionManager, messageManager);
        udpNotifier.start();
        logger.log("📡 UDP Notifier started on port " + udpPort);
    }

    private void acceptClients() {
        new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(30000);

                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    logger.log("🔗 New client connected from " + clientIP);
                    serverGUI.log("🔗 New connection: " + clientIP);

                    EnhancedClientHandler handler = new EnhancedClientHandler(
                            clientSocket,
                            userManager,
                            messageManager,
                            sessionManager,
                            udpNotifier,
                            logger,
                            serverGUI
                    );

                    threadPool.execute(handler);

                } catch (IOException e) {
                    if (running) {
                        logger.logError("Client Connection", e.getMessage());
                    }
                }
            }
        }, "Client-Acceptor").start();
    }

    private void startMaintenanceTasks() {
        maintenanceScheduler.scheduleAtFixedRate(() -> {
            checkAutoAway();
        }, 5, 10, TimeUnit.SECONDS);

        maintenanceScheduler.scheduleAtFixedRate(() -> {
            updateOnlineUsers();
        }, 2, 5, TimeUnit.SECONDS);

        maintenanceScheduler.scheduleAtFixedRate(() -> {
            sessionManager.cleanupInactiveSessions();
            logger.log("🧹 Cleaned inactive sessions");
        }, 1, 1, TimeUnit.MINUTES);

        maintenanceScheduler.scheduleAtFixedRate(() -> {
            int removed = messageManager.cleanupOldMessages(cleanupDays);
            if (removed > 0) {
                logger.logCleanup(cleanupDays, removed);
                serverGUI.log("🧹 Cleaned " + removed + " old archived messages");
            }
        }, 1, 1, TimeUnit.HOURS);

        maintenanceScheduler.scheduleAtFixedRate(() -> {
            logger.printStatistics();
        }, 5, 5, TimeUnit.MINUTES);

        logger.log("🔄 Maintenance tasks scheduled");
    }

    private void checkAutoAway() {
        sessionManager.getAllActiveSessions().forEach(session -> {
            long inactiveTime = System.currentTimeMillis() - session.getLastActivity().getTime();
            if (inactiveTime > 30000 && "ACTIVE".equals(session.getStatus())) {
                String username = session.getUsername();
                String oldStatus = session.getStatus();

                session.setStatus("AWAY");
                sessionManager.updateUserStatus(username, "AWAY");
                userManager.updateUserStatus(username, "AWAY");

                logger.logRosterChange(username, oldStatus, "AWAY");

                if (udpNotifier != null) {
                    udpNotifier.broadcastStatus(username, "AWAY");
                }

                serverGUI.log("👤 Auto-Away: " + username + " is now AWAY");
            }
        });
    }

    private void updateOnlineUsers() {
        if (serverGUI != null) {
            serverGUI.refreshOnlineUsers(sessionManager.getOnlineUsers());
        }

        if (udpNotifier != null) {
            udpNotifier.broadcastOnlineList();
        }
    }

    public void updateConfiguration(int cleanupDays, int udpPort) {
        this.cleanupDays = cleanupDays;
        this.udpPort = udpPort;

        logger.log("⚙️ Configuration updated - Cleanup: " + cleanupDays +
                " days, UDP Port: " + udpPort);

        if (udpNotifier != null && udpNotifier.isRunning()) {
            udpNotifier.stopNotifier();
            startUDPNotifier();
        }
    }

    public void stop() {
        if (!running) return;

        running = false;
        logger.log("🛑 Server shutting down...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            if (udpNotifier != null) {
                udpNotifier.stopNotifier();
            }

            if (threadPool != null) {
                threadPool.shutdownNow();
            }

            if (maintenanceScheduler != null) {
                maintenanceScheduler.shutdownNow();
            }

            if (logger != null) {
                logger.close();
            }

            logger.log("✅ Server stopped gracefully");
            if (serverGUI != null) {
                serverGUI.log("✅ Server stopped");
                serverGUI.clearOnlineUsers();
            }

        } catch (IOException e) {
            logger.logError("Shutdown", e.getMessage());
        }
    }

    // ========== Getters ==========
    public SessionManager getSessionManager() { return sessionManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public UserManager getUserManager() { return userManager; }
    public UDPNotifier getUdpNotifier() { return udpNotifier; }
    public EnhancedLogger getLogger() { return logger; }
    public boolean isRunning() { return running; }
    public int getCleanupDays() { return cleanupDays; }
    public int getUdpPort() { return udpPort; }
}