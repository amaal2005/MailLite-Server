// server/storage/UserManager.java
package server.storage;

import server.models.User;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class UserManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private static final String USERS_FILE = "data/users.txt";

    public UserManager() {
        System.out.println("🔍 ===== FILE LOCATION DEBUG =====");
        System.out.println("Current Working Directory: " + new File(".").getAbsolutePath());

        File usersFile = new File(USERS_FILE);
        System.out.println("📂 Users file will be at: " + usersFile.getAbsolutePath());

        File oldFile = new File("users.dat");
        if (oldFile.exists()) {
            oldFile.delete();
            System.out.println("🗑️ Deleted old users.dat file");
        }

        ensureDataDirectory();
        loadUsers();
        if (users.isEmpty()) {
            createDefaultUsers();
            saveUsers();
        }
        System.out.println("✅ UserManager initialized with " + users.size() + " users");
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
        if (!file.exists()) {
            System.out.println("📝 No users file found - will create new");
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
        if (users.isEmpty()) {
            System.out.println("Creating default users...");
            addUser("admin", "admin");
            addUser("user1", "123");
            addUser("user2", "123");
            addUser("test", "test");
            addUser("enas", "123");
            addUser("ahmad", "123");
            addUser("sara", "123");
            saveUsers();
            System.out.println("Default users created successfully");
        }
    }

    public boolean authenticateUser(String username, String password) {
        if (username == null || password == null || username.trim().isEmpty()) {
            return false;
        }

        String key = username.trim().toLowerCase();
        User user = users.get(key);

        if (user == null) {
            System.out.println("AUTH FAILED - User not found: " + username);
            return false;
        }

        if (user.getPassword().equals(password)) {
            user.setLastLogin(System.currentTimeMillis());
            user.setStatus("ACTIVE");
            saveUsers();
            System.out.println("AUTH SUCCESS - " + user.getUsername());
            return true;
        } else {
            System.out.println("AUTH FAILED - Wrong password for: " + username);
            return false;
        }
    }

    public boolean addUser(String username, String password) {
        if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
            return false;
        }

        String key = username.trim().toLowerCase();
        if (users.containsKey(key)) {
            System.out.println("ADD USER FAILED - Already exists: " + username);
            return false;
        }

        User newUser = new User(key, password);
        users.put(key, newUser);
        saveUsers();
        System.out.println("USER ADDED - " + key);
        return true;
    }

    public void updateUserStatus(String username, String status) {
        User user = users.get(username.toLowerCase());
        if (user != null) {
            user.setStatus(status);
            saveUsers();
        }
    }



    public boolean removeUser(String username) {
        String key = username.trim().toLowerCase();
        if (users.remove(key) != null) {
            saveUsers();
            System.out.println("USER REMOVED - " + key);
            return true;
        }
        System.out.println("REMOVE FAILED - User not found: " + username);
        return false;
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

    public boolean changePassword(String username, String oldPass, String newPass) {
        User user = users.get(username.toLowerCase());
        if (user != null && user.getPassword().equals(oldPass)) {
            user.setPassword(newPass);
            saveUsers();
            return true;
        }
        return false;
    }

    public void reset() {
        users.clear();
        new File(USERS_FILE).delete();
        createDefaultUsers();
        System.out.println("UserManager has been RESET");
    }

    public int getTotalUsers() {
        return users.size();
    }
}