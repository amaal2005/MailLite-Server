// src/main/java/server/storage/SimpleUserManager.java
package server.storage;

import server.models.User;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleUserManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private static final String USERS_FILE = "data/users.txt";

    public SimpleUserManager() {
        System.out.println("🔍 ===== FILE LOCATION DEBUG =====");
        System.out.println("Current Working Directory: " + new File(".").getAbsolutePath());

        File usersFile = new File(USERS_FILE);
        System.out.println("📂 Users file will be at: " + usersFile.getAbsolutePath());

        ensureDataDirectory();
        loadUsers();
        if (users.isEmpty()) {
            createDefaultUsers();
            saveUsers();
        }
        System.out.println("✅ SimpleUserManager initialized with " + users.size() + " users");
    }

    private void ensureDataDirectory() {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            if (dataDir.mkdirs()) {
                System.out.println("📁 Created data directory");
            } else {
                System.err.println("❌ Failed to create data directory!");
            }
        }
    }

    private void loadUsers() {
        File file = new File(USERS_FILE);
        System.out.println("🔍 Looking for users file at: " + file.getAbsolutePath());

        if (!file.exists()) {
            System.out.println("📝 File not found - will create new");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    User user = new User();
                    user.setUsername(parts[0]);
                    user.setPassword(parts[1]);
                    user.setStatus(parts[2]);

                    try {
                        user.setLastLogin(Long.parseLong(parts[3]));
                        user.setLastSeen(Long.parseLong(parts[4]));
                    } catch (NumberFormatException e) {
                        long now = System.currentTimeMillis();
                        user.setLastLogin(now);
                        user.setLastSeen(now);
                    }

                    users.put(user.getUsername().toLowerCase(), user);
                    count++;
                }
            }
            System.out.println("✅ Loaded " + count + " users");

        } catch (Exception e) {
            System.err.println("❌ Error loading users: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try {
            File file = new File(USERS_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("# MailLite Users File");
                writer.println("# Format: username|password|status|lastLogin|lastSeen");
                writer.println("# Saved at: " + new Date());
                writer.println();

                for (User user : users.values()) {
                    writer.printf("%s|%s|%s|%d|%d%n",
                            user.getUsername(),
                            user.getPassword(),
                            user.getStatus(),
                            user.getLastLogin(),
                            user.getLastSeen());
                }
            }

            System.out.println("💾 Saved " + users.size() + " users");

        } catch (Exception e) {
            System.err.println("❌ Error saving users: " + e.getMessage());
        }
    }

    private void createDefaultUsers() {
        System.out.println("📝 Creating default users...");

        String[][] defaultUsers = {
                {"admin", "admin123"},
                {"user1", "password1"},
                {"user2", "password2"},
                {"test", "test123"},
                {"enas", "enas123"},
                {"ahmad", "ahmad123"},
                {"sara", "sara123"}
        };

        for (String[] userPass : defaultUsers) {
            if (userPass.length == 2) {
                addUserInternal(userPass[0], userPass[1]);
            }
        }
    }

    private void addUserInternal(String username, String password) {
        User user = new User(username, password);
        user.setStatus("OFFLINE");
        users.put(username.toLowerCase(), user);
    }

    // ========== Public Methods ==========

    public boolean authenticateUser(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        String key = username.trim().toLowerCase();
        User user = users.get(key);

        if (user == null) {
            System.out.println("❌ User not found: " + username);
            return false;
        }

        if (user.getPassword().equals(password)) {
            user.setLastLogin(System.currentTimeMillis());
            user.setStatus("ACTIVE");
            user.setLastSeen(System.currentTimeMillis());
            saveUsers();
            System.out.println("✅ Authentication successful: " + username);
            return true;
        }

        System.out.println("❌ Wrong password for user: " + username);
        return false;
    }

    public boolean addUser(String username, String password) {
        if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
            return false;
        }

        String key = username.trim().toLowerCase();
        if (users.containsKey(key)) {
            System.out.println("❌ User already exists: " + username);
            return false;
        }

        User newUser = new User(key, password);
        newUser.setStatus("OFFLINE");
        users.put(key, newUser);

        saveUsers();
        System.out.println("✅ User added: " + username);
        return true;
    }

    public boolean removeUser(String username) {
        String key = username.trim().toLowerCase();
        if (users.remove(key) != null) {
            saveUsers();
            System.out.println("✅ User removed: " + username);
            return true;
        }
        System.out.println("❌ User not found: " + username);
        return false;
    }

    public void updateUserStatus(String username, String status) {
        User user = users.get(username.toLowerCase());
        if (user != null) {
            user.setStatus(status);
            user.setLastSeen(System.currentTimeMillis());
            saveUsers();
        }
    }

    public boolean userExists(String username) {
        return username != null && users.containsKey(username.trim().toLowerCase());
    }

    public User getUser(String username) {
        return users.get(username.toLowerCase());
    }

    public List<String> getAllUsernames() {
        return new ArrayList<>(users.keySet());
    }

    public int getTotalUsers() {
        return users.size();
    }
}