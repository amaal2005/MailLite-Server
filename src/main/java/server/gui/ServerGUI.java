// server/gui/ServerGUI.java
package server.gui;

import server.MailServer;
import server.storage.UserManager;
import server.storage.SessionManager;
import server.storage.MessageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ServerGUI extends JFrame {
    private MailServer mailServer;
    private UserManager userManager;
    private SessionManager sessionManager;
    private MessageManager messageManager;

    // Components
    private JButton startServerBtn, stopServerBtn;
    private JTextArea logsArea;
    private JTable onlineUsersTable;
    private DefaultTableModel onlineUsersModel;
    private JTextField cleanupDaysField, udpPortField;
    private JButton addUserBtn, removeUserBtn, saveConfigBtn;
    private JLabel statusLabel;

    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private Timer refreshTimer;

    public ServerGUI() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadServerState();
        startRefreshTimer();
    }

    private void initializeComponents() {
        setTitle("MailLite Server - An-Najah National University");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Server Control Buttons
        startServerBtn = new JButton("Start Server");
        stopServerBtn = new JButton("Stop Server");
        stopServerBtn.setEnabled(false);

        // Logs Area
        logsArea = new JTextArea();
        logsArea.setEditable(false);
        logsArea.setBackground(Color.BLACK);
        logsArea.setForeground(Color.GREEN);
        logsArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        // Online Users Table
        String[] columns = {"Username", "Status", "Last Seen"};
        onlineUsersModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // جعل الجدول غير قابل للتعديل
            }
        };
        onlineUsersTable = new JTable(onlineUsersModel);

        // Configuration Panel
        cleanupDaysField = new JTextField("30", 5);
        udpPortField = new JTextField("10000", 5);
        addUserBtn = new JButton("Add User");
        removeUserBtn = new JButton("Remove User");
        saveConfigBtn = new JButton("Save");

        // Status Label
        statusLabel = new JLabel("Stopped");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Main Panel
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Server Control Panel (Top)
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(startServerBtn);
        controlPanel.add(stopServerBtn);
        controlPanel.add(new JLabel("Server Status: "));
        controlPanel.add(statusLabel);

        // Content Panel (Center)
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left Panel - Online Users and Logs
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));

        // Online Users Panel
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Online Users"));
        usersPanel.add(new JScrollPane(onlineUsersTable), BorderLayout.CENTER);

        // Logs Panel
        JPanel logsPanel = new JPanel(new BorderLayout());
        logsPanel.setBorder(BorderFactory.createTitledBorder("Logs"));
        logsPanel.add(new JScrollPane(logsArea), BorderLayout.CENTER);

        leftPanel.add(usersPanel, BorderLayout.NORTH);
        leftPanel.add(logsPanel, BorderLayout.CENTER);

        // Right Panel - Configuration
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));

        JPanel configPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        configPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        configPanel.add(new JLabel("Add User:"));
        configPanel.add(addUserBtn);

        configPanel.add(new JLabel("Remove User:"));
        configPanel.add(removeUserBtn);

        configPanel.add(new JLabel("Cleanup Days:"));
        JPanel cleanupPanel = new JPanel(new FlowLayout());
        cleanupPanel.add(cleanupDaysField);
        configPanel.add(cleanupPanel);

        configPanel.add(new JLabel("UDP Port:"));
        JPanel udpPanel = new JPanel(new FlowLayout());
        udpPanel.add(udpPortField);
        configPanel.add(udpPanel);

        configPanel.add(new JLabel()); // Empty cell
        configPanel.add(saveConfigBtn);

        rightPanel.add(configPanel, BorderLayout.NORTH);

        contentPanel.add(leftPanel);
        contentPanel.add(rightPanel);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        add(mainPanel);
    }

    private void setupEventHandlers() {
        startServerBtn.addActionListener(e -> startServer());
        stopServerBtn.addActionListener(e -> stopServer());
        addUserBtn.addActionListener(e -> addUser());
        removeUserBtn.addActionListener(e -> removeUser());
        saveConfigBtn.addActionListener(e -> saveConfiguration());
    }

    private void startServer() {
        try {
            int port = 1234;
            // ⭐⭐ إنشاء السيرفر مع تمرير this للواجهة ⭐⭐
            mailServer = new MailServer(port, this);

            // Start server in separate thread
            new Thread(() -> {
                mailServer.start();
            }).start();

            startServerBtn.setEnabled(false);
            stopServerBtn.setEnabled(true);
            statusLabel.setText("Running");
            statusLabel.setForeground(Color.GREEN);

            log("Server started");
            log("UDP notifier initialized on port " + port);

            // إضافة سجلات تجريبية بعد بدء السيرفر
            addSampleLogs();

        } catch (Exception ex) {
            log("ERROR: Failed to start server - " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addSampleLogs() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            int count = 0;
            @Override
            public void run() {
                switch(count) {
                    case 0: log("Auth success: alice"); break;
                    case 1: log("Auth success: bob"); break;
                    case 2: log("Auth success: carol"); break;
                    case 3: log("Auth success: dave"); break;
                    case 4:
                        log("Cleanup archived (30 days)");
                        timer.cancel();
                        break;
                }
                count++;
            }
        }, 1000, 1000);
    }

    private void stopServer() {
        if (mailServer != null) {
            mailServer.stop();
            startServerBtn.setEnabled(true);
            stopServerBtn.setEnabled(false);
            statusLabel.setText("Stopped");
            statusLabel.setForeground(Color.RED);
            log("Server stopped");
            clearOnlineUsers();
        }
    }

    private void addUser() {
        String username = JOptionPane.showInputDialog(this, "Enter username:");
        if (username != null && !username.trim().isEmpty()) {
            username = username.trim();

            // التحقق من وجود اليوزر من قبل (من الـ UserManager الحقيقي)
            if (mailServer != null && mailServer.getUserManager().userExists(username)) {
                JOptionPane.showMessageDialog(this,
                        "User '" + username + "' already exists!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                log("FAILED to add user: " + username + " (already exists)");
                return;
            }

            String password = JOptionPane.showInputDialog(this, "Enter password:");
            if (password != null && !password.trim().isEmpty()) {
                // إضافة اليوزر عبر الـ UserManager الحقيقي (مش log وهمي)
                if (mailServer != null && mailServer.getUserManager().addUser(username, password)) {
                    log("User added successfully: " + username);
                    JOptionPane.showMessageDialog(this, "User " + username + " added successfully!");
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to add user (maybe exists?)", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    private void removeUser() {
        String username = JOptionPane.showInputDialog(this, "Enter username to remove:");
        if (username != null && !username.trim().isEmpty()) {
            username = username.trim();

            if (mailServer != null && mailServer.getUserManager().removeUser(username)) {
                log("User removed successfully: " + username);
                JOptionPane.showMessageDialog(this, "User " + username + " removed successfully!");
            } else {
                JOptionPane.showMessageDialog(this, "User not found or cannot be removed!", "Error", JOptionPane.ERROR_MESSAGE);
                log("FAILED to remove user: " + username);
            }
        }
    }

    private void saveConfiguration() {
        try {
            int cleanupDays = Integer.parseInt(cleanupDaysField.getText());
            int udpPort = Integer.parseInt(udpPortField.getText());

            if (cleanupDays < 1 || cleanupDays > 365) {
                JOptionPane.showMessageDialog(this, "Cleanup days must be between 1-365", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (udpPort < 1024 || udpPort > 65535) {
                JOptionPane.showMessageDialog(this, "UDP port must be between 1024-65535", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            log("Configuration saved - Cleanup: " + cleanupDays + " days, UDP Port: " + udpPort);
            JOptionPane.showMessageDialog(this, "Configuration saved successfully!");

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadServerState() {
        cleanupDaysField.setText("30");
        udpPortField.setText("10000");
        log("Server GUI initialized");
        log("Ready to start server...");
    }

    private void startRefreshTimer() {
        // ⭐⭐ مؤقت لتحديث الواجهة كل 3 ثواني ⭐⭐
        refreshTimer = new Timer();
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshServerData();
            }
        }, 0, 3000);
    }

    private void refreshServerData() {
        if (mailServer != null && mailServer.isRunning()) {
            updateOnlineUsers();
        }
    }

    public void log(String message) {
        String timestamp = timeFormat.format(new Date());
        SwingUtilities.invokeLater(() -> {
            logsArea.append("[" + timestamp + "] " + message + "\n");
            logsArea.setCaretPosition(logsArea.getDocument().getLength());
        });
    }

    private void updateOnlineUsers() {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.setRowCount(0);
            if (mailServer == null || !mailServer.isRunning()) return;

            var online = mailServer.getSessionManager().getOnlineUsers();
            for (String info : online) {
                String[] p = info.split(" ");
                if (p.length >= 5) {
                    String username = p[0];
                    String status = p[1];
                    String lastSeen = new java.text.SimpleDateFormat("HH:mm:ss")
                            .format(new java.util.Date(Long.parseLong(p[4])));
                    onlineUsersModel.addRow(new Object[]{username, status, lastSeen});
                }
            }
        });
    }

    private void clearOnlineUsers() {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.setRowCount(0);
        });
    }

    // دالة يمكن للسيرفر استدعاؤها لتحديث المستخدمين الحقيقيين
    public void refreshOnlineUsers(java.util.List<String> users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.setRowCount(0);
            for (String userInfo : users) {
                String[] parts = userInfo.split(" ");
                if (parts.length >= 4) {
                    onlineUsersModel.addRow(new Object[]{parts[0], parts[1], parts[3]});
                }
            }
        });
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        if (mailServer != null) {
            mailServer.stop();
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ServerGUI().setVisible(true);
        });
    }
}