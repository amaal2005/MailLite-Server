package server;

import server.gui.ServerGUI;
import server.handlers.ClientHandler;
import server.storage.UserManager;
import server.storage.MessageManager;
import server.storage.SessionManager;
import server.utils.Logger;

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
    private boolean running;
    private final int port;
    private final Logger logger;

    // الـ managers المشتركة (نسخة واحدة لكل السيرفر)
    private final UserManager userManager;
    private final MessageManager messageManager;
    private final SessionManager sessionManager;

    private final ServerGUI serverGUI;

    public MailServer(int port, ServerGUI gui) {
        this.port = port;
        this.serverGUI = gui;
        this.logger = new Logger();

        // إنشاء نسخة واحدة فقط من كل مدير
        this.userManager = new UserManager();
        this.messageManager = new MessageManager();
        this.sessionManager = new SessionManager();

        this.threadPool = Executors.newCachedThreadPool();
        this.maintenanceScheduler = Executors.newScheduledThreadPool(1);
        this.running = false;

        logToGUI("MailServer initialized successfully");
        System.out.println("MailServer ready on port " + port);
    }

    private void logToGUI(String message) {
        if (serverGUI != null) {
            serverGUI.log(message);
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;

            logToGUI("Server started on port " + port);
            logger.log("MailLite Server STARTED on port " + port);

            startMaintenanceTasks();
            addShutdownHook();

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(60000);

                    logToGUI("New client: " + clientSocket.getInetAddress());
                    logger.log("Client connected: " + clientSocket.getInetAddress());

                    // تمرير الـ managers المشتركة + الـ GUI لكل عميل
                    ClientHandler handler = new ClientHandler(
                            clientSocket,
                            userManager,
                            messageManager,
                            sessionManager,
                            serverGUI
                    );

                    threadPool.execute(handler);

                } catch (IOException e) {
                    if (running) {
                        logToGUI("Connection error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logToGUI("FATAL: Cannot start server - " + e.getMessage());
            logger.log("Server failed: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void startMaintenanceTasks() {
        maintenanceScheduler.scheduleAtFixedRate(() -> {
            sessionManager.cleanupInactiveSessions();
            logToGUI("Cleaned inactive sessions");
        }, 5, 5, TimeUnit.MINUTES);

        maintenanceScheduler.scheduleAtFixedRate(() -> {
            messageManager.cleanupOldMessages(30);
            logToGUI("Cleaned old messages (30+ days)");
        }, 1, 1, TimeUnit.HOURS);
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        if (!running) return;
        running = false;
        logToGUI("Server shutting down...");

        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (threadPool != null) threadPool.shutdownNow();
        if (maintenanceScheduler != null) maintenanceScheduler.shutdownNow();

        logToGUI("Server stopped gracefully");
        logger.log("Server stopped");
        logger.close();
    }

    // Getters مهمة للواجهة
    public SessionManager getSessionManager() { return sessionManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public UserManager getUserManager() { return userManager; }
    public boolean isRunning() { return running; }
}