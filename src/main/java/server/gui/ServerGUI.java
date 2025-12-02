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
import java.util.List;

public class ServerGUI extends JFrame {
    private MailServer mailServer;
    private UserManager userManager;
    private SessionManager sessionManager;
    private MessageManager messageManager;

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

        startServerBtn = new JButton("Start Server");
        stopServerBtn = new JButton("Stop Server");
        stopServerBtn.setEnabled(false);

        logsArea = new JTextArea();
        logsArea.setEditable(false);
        logsArea.setBackground(Color.BLACK);
        logsArea.setForeground(Color.GREEN);
        logsArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        String[] columns = {"Username", "Status", "IP Address", "Last Seen"};
        onlineUsersModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        onlineUsersTable = new JTable(onlineUsersModel);
        onlineUsersTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        onlineUsersTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        onlineUsersTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        onlineUsersTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        cleanupDaysField = new JTextField("30", 5);
        udpPortField = new JTextField("10000", 5);
        addUserBtn = new JButton("Add User");
        removeUserBtn = new JButton("Remove User");
        saveConfigBtn = new JButton("Save Configuration");

        statusLabel = new JLabel("Stopped");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(startServerBtn);
        controlPanel.add(stopServerBtn);
        controlPanel.add(new JLabel("Server Status: "));
        controlLabel();
        controlPanel.add(statusLabel);

        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Online Users"));
        usersPanel.add(new JScrollPane(onlineUsersTable), BorderLayout.CENTER);

        JPanel logsPanel = new JPanel(new BorderLayout());
        logsPanel.setBorder(BorderFactory.createTitledBorder("Server Logs"));
        logsPanel.add(new JScrollPane(logsArea), BorderLayout.CENTER);

        leftPanel.add(usersPanel, BorderLayout.NORTH);
        leftPanel.add(logsPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Server Configuration"));

        JPanel configPanel = new JPanel(new GridLayout(8, 2, 5, 5));
        configPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        configPanel.add(new JLabel("Add New User:"));
        configPanel.add(addUserBtn);

        configPanel.add(new JLabel("Remove User:"));
        configPanel.add(removeUserBtn);

        configPanel.add(new JLabel(""));
        configPanel.add(new JLabel(""));

        configPanel.add(new JLabel("Cleanup Days (1-365):"));
        JPanel cleanupPanel = new JPanel(new FlowLayout());
        cleanupPanel.add(cleanupDaysField);
        configPanel.add(cleanupPanel);

        configPanel.add(new JLabel("UDP Port:"));
        JPanel udpPanel = new JPanel(new FlowLayout());
        udpPanel.add(udpPortField);
        configPanel.add(udpPanel);

        configPanel.add(new JLabel(""));
        configPanel.add(saveConfigBtn);

        JPanel statsPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Statistics"));
        statsPanel.add(new JLabel("Total Users: 0"));
        statsPanel.add(new JLabel("Total Messages: 0"));
        statsPanel.add(new JLabel("Active Sessions: 0"));

        rightPanel.add(configPanel, BorderLayout.NORTH);
        rightPanel.add(statsPanel, BorderLayout.SOUTH);

        contentPanel.add(leftPanel);
        contentPanel.add(rightPanel);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        add(mainPanel);
    }

    private void controlLabel() {
        // هذه الدالة فارغة - ربما بقايا من كود قديم
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
            int cleanupDays = Integer.parseInt(cleanupDaysField.getText());
            int udpPort = Integer.parseInt(udpPortField.getText());

            if (cleanupDays < 1 || cleanupDays > 365) {
                JOptionPane.showMessageDialog(this, "Cleanup days must be between 1-365",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (udpPort < 1024 || udpPort > 65535) {
                JOptionPane.showMessageDialog(this, "UDP port must be between 1024-65535",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int tcpPort = 1234; // Port افتراضي
            mailServer = new MailServer(tcpPort, this);

            // تحديث الإعدادات
            mailServer.updateConfiguration(cleanupDays, udpPort);

            new Thread(() -> {
                mailServer.start();
            }).start();

            startServerBtn.setEnabled(false);
            stopServerBtn.setEnabled(true);
            statusLabel.setText("Running");
            statusLabel.setForeground(Color.GREEN);

            log("✅ Server started on TCP port " + tcpPort);
            log("📡 UDP Notifier on port " + udpPort);
            log("🧹 Cleanup set to " + cleanupDays + " days");

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers!",
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            log("❌ ERROR: Failed to start server - " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (mailServer != null) {
            mailServer.stop();
            startServerBtn.setEnabled(true);
            stopServerBtn.setEnabled(false);
            statusLabel.setText("Stopped");
            statusLabel.setForeground(Color.RED);
            log("🛑 Server stopped");
            clearOnlineUsers(); // ✅ تم التصحيح هنا - أصبحت public
            updateStats();
        }
    }

    private void addUser() {
        String username = JOptionPane.showInputDialog(this, "Enter username:");
        if (username != null && !username.trim().isEmpty()) {
            username = username.trim();

            if (mailServer != null && mailServer.getUserManager().userExists(username)) {
                JOptionPane.showMessageDialog(this,
                        "User '" + username + "' already exists!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                log("❌ FAILED to add user: " + username + " (already exists)");
                return;
            }

            String password = JOptionPane.showInputDialog(this, "Enter password:");
            if (password != null && !password.trim().isEmpty()) {
                if (mailServer != null) {
                    if (mailServer.getUserManager().addUser(username, password)) {
                        log("✅ User added successfully: " + username);
                        JOptionPane.showMessageDialog(this, "User " + username + " added successfully!");
                        updateStats();
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to add user!",
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }

    private void removeUser() {
        String username = JOptionPane.showInputDialog(this, "Enter username to remove:");
        if (username != null && !username.trim().isEmpty()) {
            username = username.trim();

            if (mailServer != null) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to remove user '" + username + "'?",
                        "Confirm Removal", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    if (mailServer.getUserManager().removeUser(username)) {
                        log("✅ User removed successfully: " + username);
                        JOptionPane.showMessageDialog(this, "User " + username + " removed successfully!");
                        updateStats();
                    } else {
                        JOptionPane.showMessageDialog(this, "User not found or cannot be removed!",
                                "Error", JOptionPane.ERROR_MESSAGE);
                        log("❌ FAILED to remove user: " + username);
                    }
                }
            }
        }
    }

    private void saveConfiguration() {
        try {
            int cleanupDays = Integer.parseInt(cleanupDaysField.getText());
            int udpPort = Integer.parseInt(udpPortField.getText());

            if (cleanupDays < 1 || cleanupDays > 365) {
                JOptionPane.showMessageDialog(this, "Cleanup days must be between 1-365",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (udpPort < 1024 || udpPort > 65535) {
                JOptionPane.showMessageDialog(this, "UDP port must be between 1024-65535",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (mailServer != null && mailServer.isRunning()) {
                mailServer.updateConfiguration(cleanupDays, udpPort);
            }

            log("⚙️ Configuration saved - Cleanup: " + cleanupDays + " days, UDP Port: " + udpPort);
            JOptionPane.showMessageDialog(this, "Configuration saved successfully!");

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format!",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadServerState() {
        cleanupDaysField.setText("30");
        udpPortField.setText("10000");
        log("✅ Server GUI initialized");
        log("🟡 Ready to start server...");
        log("🔧 Default port: 1234, UDP: 10000, Cleanup: 30 days");
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer();
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshServerData();
            }
        }, 0, 3000); // تحديث كل 3 ثواني
    }

    private void refreshServerData() {
        if (mailServer != null && mailServer.isRunning()) {
            updateOnlineUsers();
            updateStats();
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

            try {
                var sessionManager = mailServer.getSessionManager();
                if (sessionManager != null) {
                    var onlineUsers = sessionManager.getOnlineUsers();

                    for (String userInfo : onlineUsers) {
                        String[] parts = userInfo.split(" ");
                        if (parts.length >= 5) {
                            String username = parts[0];
                            String status = parts[1];
                            String ip = parts[2];
                            String udpPort = parts[3];
                            long lastSeenMillis = Long.parseLong(parts[4]);

                            String lastSeen = formatTimeAgo(lastSeenMillis);

                            // تحديد لون حسب الحالة
                            Color statusColor = Color.BLACK;
                            switch (status) {
                                case "ACTIVE": statusColor = Color.GREEN.darker(); break;
                                case "BUSY": statusColor = Color.ORANGE.darker(); break;
                                case "AWAY": statusColor = Color.GRAY; break;
                                case "OFFLINE": statusColor = Color.RED; break;
                            }

                            onlineUsersModel.addRow(new Object[]{
                                    username,
                                    "<html><b><font color='" + getHexColor(statusColor) + "'>" + status + "</font></b></html>",
                                    ip + ":" + udpPort,
                                    lastSeen
                            });
                        }
                    }
                }
            } catch (Exception e) {
                log("❌ Error updating online users: " + e.getMessage());
            }
        });
    }

    private String formatTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60000) return "Just now";
        if (diff < 3600000) return (diff / 60000) + " min ago";
        if (diff < 86400000) return (diff / 3600000) + " hours ago";
        return (diff / 86400000) + " days ago";
    }

    private String getHexColor(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    // ✅ أصبحت public بدل private
    public void clearOnlineUsers() {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.setRowCount(0);
        });
    }

    public void refreshOnlineUsers(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.setRowCount(0);
            for (String userInfo : users) {
                String[] parts = userInfo.split(" ");
                if (parts.length >= 4) {
                    onlineUsersModel.addRow(new Object[]{
                            parts[0],
                            parts[1],
                            parts[2],
                            parts.length > 3 ? parts[3] : "N/A"
                    });
                }
            }
        });
    }

    private void updateStats() {
        SwingUtilities.invokeLater(() -> {
            if (mailServer != null) {
                try {
                    int totalUsers = mailServer.getUserManager().getTotalUsers();
                    int totalMessages = mailServer.getMessageManager().getTotalMessagesCount();
                    int activeSessions = mailServer.getSessionManager().getOnlineCount();

                    // تحديث الـ labels في الواجهة
                    Component[] components = getContentPane().getComponents();
                    for (Component comp : components) {
                        if (comp instanceof JPanel) {
                            updatePanelStats((JPanel) comp, totalUsers, totalMessages, activeSessions);
                        }
                    }
                } catch (Exception e) {
                    log("❌ Error updating stats: " + e.getMessage());
                }
            }
        });
    }

    private void updatePanelStats(JPanel panel, int users, int messages, int sessions) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                updatePanelStats((JPanel) comp, users, messages, sessions);
            } else if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String text = label.getText();
                if (text.contains("Total Users:")) {
                    label.setText("Total Users: " + users);
                } else if (text.contains("Total Messages:")) {
                    label.setText("Total Messages: " + messages);
                } else if (text.contains("Active Sessions:")) {
                    label.setText("Active Sessions: " + sessions);
                }
            }
        }
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
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            ServerGUI serverGUI = new ServerGUI();
            serverGUI.setVisible(true);
        });
    }
}