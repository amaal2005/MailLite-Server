package server.storage;

import server.models.User;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private static final String USERS_FILE = "users.dat";

    public UserManager() {
        loadUsers();
        createDefaultUsers();
        System.out.println("✅ UserManager initialized with " + users.size() + " users");
    }

    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            System.out.println("📂 No users file found → starting fresh");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, User> loaded = (Map<String, User>) ois.readObject();

            for (Map.Entry<String, User> entry : loaded.entrySet()) {
                String lowerKey = entry.getKey().toLowerCase();
                User user = entry.getValue();
                user.setUsername(lowerKey);
                users.put(lowerKey, user);
            }
            System.out.println("✅ Loaded " + users.size() + " users from " + USERS_FILE);
        } catch (Exception e) {
            System.out.println("❌ Failed to load users: " + e.getMessage());
            users.clear();
        }
    }

    private void createDefaultUsers() {
        if (users.isEmpty()) {
            System.out.println("👤 Creating default users...");
            addUser("admin", "admin");
            addUser("user1", "123");
            addUser("user2", "123");
            addUser("test", "test");
            addUser("enas", "123");
            addUser("ahmad", "123");
            addUser("sara", "123");
            saveUsers(); // حفظ فوري
            System.out.println("✅ Default users created successfully");
        }
    }

    public boolean authenticateUser(String username, String password) {
        if (username == null || password == null || username.trim().isEmpty()) {
            return false;
        }

        String key = username.trim().toLowerCase();
        User user = users.get(key);

        if (user == null) {
            System.out.println("❌ AUTH FAILED → User not found: " + username);
            return false;
        }

        if (user.getPassword().equals(password)) {
            user.setLastLogin(System.currentTimeMillis());
            user.setStatus("ACTIVE");
            saveUsers(); // حفظ فوري
            System.out.println("✅ AUTH SUCCESS → " + user.getUsername());
            return true;
        } else {
            System.out.println("❌ AUTH FAILED → Wrong password for: " + username);
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
            System.out.println("❌ ADD USER FAILED → Already exists: " + username);
            return false;
        }

        User newUser = new User(key, password);
        users.put(key, newUser);
        saveUsers(); // حفظ فوري
        System.out.println("✅ USER ADDED → " + key);
        return true;
    }

    public void updateUserStatus(String username, String status) {
        User user = users.get(username.toLowerCase());
        if (user != null) {
            user.setStatus(status);
            saveUsers(); // حفظ فوري
        }
    }

    private void saveUsers() {
        try {
            File file = new File(USERS_FILE);

            // التصحيح: التحقق من وجود parent directory
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(users);
                System.out.println("💾 Saved " + users.size() + " users to disk: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to save users: " + e.getMessage());
        }
    }

    // باقي الدوال كما هي...
    public boolean removeUser(String username) {
        String key = username.trim().toLowerCase();
        if (users.remove(key) != null) {
            saveUsers();
            System.out.println("✅ USER REMOVED → " + key);
            return true;
        }
        System.out.println("❌ REMOVE FAILED → User not found: " + username);
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
        System.out.println("🔄 UserManager has been RESET");
    }

    public int getTotalUsers() {
        return users.size();
    }
}