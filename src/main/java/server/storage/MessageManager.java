package server.storage;

import server.models.Message;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MessageManager {
    private final Map<String, List<Message>> userMessages;
    private static final String MESSAGES_FILE = "data/messages.dat";
    private AtomicLong nextId;

    private Map<String, Integer> sendCounts = new ConcurrentHashMap<>();
    private Map<String, Integer> listCounts = new ConcurrentHashMap<>();
    private Map<String, Integer> retrCounts = new ConcurrentHashMap<>();

    public MessageManager() {
        this.userMessages = new ConcurrentHashMap<>();
        this.nextId = new AtomicLong(1);
        loadMessages();
        System.out.println("✅ MessageManager initialized");
    }

    public String saveMessage(String from, String recipients, String subject, String body) {
        try {
            String messageId = "MSG_" + System.currentTimeMillis() + "_" + nextId.getAndIncrement();
            List<String> recipList = Arrays.stream(recipients.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (recipList.isEmpty()) return null;

            long timestamp = System.currentTimeMillis();

            for (String to : recipList) {
                Message msg = new Message(messageId, from, to, subject, body, timestamp);
                String userKey = to.toLowerCase();
                userMessages.computeIfAbsent(userKey, k -> new ArrayList<>()).add(0, msg);
            }

            Message sentMsg = new Message(messageId, from, recipList, subject, body, timestamp);
            String sentKey = from.toLowerCase() + "_sent";
            userMessages.computeIfAbsent(sentKey, k -> new ArrayList<>()).add(0, sentMsg);

            sendCounts.merge(from, 1, Integer::sum);

            saveMessages();

            System.out.println("📤 Message saved: " + messageId + " from " + from + " to " + recipients);
            return messageId;

        } catch (Exception e) {
            System.err.println("❌ Error saving message: " + e.getMessage());
            return null;
        }
    }

    public List<Message> getMessagesForUser(String username, String folder) {
        String key = folder.equalsIgnoreCase("SENT") ?
                username.toLowerCase() + "_sent" :
                username.toLowerCase();

        List<Message> messages = userMessages.getOrDefault(key, new ArrayList<>());

        List<Message> sortedMessages = new ArrayList<>(messages);
        sortedMessages.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));

        return sortedMessages;
    }

    public List<String> listMessages(String username, String folder) {
        List<Message> messages = getMessagesForUser(username, folder);
        List<String> result = new ArrayList<>();

        for (Message msg : messages) {
            boolean include = true;

            if ("ARCHIVE".equalsIgnoreCase(folder)) {
                if (!msg.isArchived()) include = false;
            } else {
                if (msg.isArchived()) include = false;
            }

            if ("UNREAD".equalsIgnoreCase(folder) && msg.isRead()) {
                include = false;
            }

            if (include) {
                String line = String.format("%s %s %d %d %s",
                        msg.getMessageId(),
                        msg.getFrom(),
                        msg.getBody().length(),
                        msg.getTimestamp(),
                        msg.getSubject());
                result.add(line);
            }
        }

        listCounts.merge(username, 1, Integer::sum);

        System.out.println("📋 LIST " + folder + " for " + username + " - " + result.size() + " messages");
        return result;
    }

    public String getMessage(String messageId, String username) {
        for (List<Message> list : userMessages.values()) {
            for (Message msg : list) {
                if (msg.getMessageId().equals(messageId)) {
                    boolean canAccess = msg.getFrom().equals(username) ||
                            msg.getToList().contains(username);

                    if (canAccess) {
                        if (msg.getToList().contains(username) && !msg.getFrom().equals(username)) {
                            msg.setRead(true);
                            saveMessages();
                        }

                        retrCounts.merge(username, 1, Integer::sum);

                        return String.format(
                                "214 FROM:%s\n214 TO:%s\n214 SUBJ:%s\n214 TIMESTAMP:%d\n214 BODYLEN:%d\n214 BODY\n%s",
                                msg.getFrom(),
                                msg.getToAsString(),
                                msg.getSubject(),
                                msg.getTimestamp(),
                                msg.getBody().length(),
                                msg.getBody()
                        );
                    }
                }
            }
        }
        return null;
    }

    public boolean archiveMessage(String messageId, String username) {
        return updateMessageArchiveStatus(messageId, username, true);
    }

    public boolean restoreMessage(String messageId, String username) {
        return updateMessageArchiveStatus(messageId, username, false);
    }

    public boolean markAsRead(String messageId, String username) {
        for (List<Message> list : userMessages.values()) {
            for (Message msg : list) {
                if (msg.getMessageId().equals(messageId) &&
                        msg.getToList().contains(username)) {
                    msg.setRead(true);
                    saveMessages();
                    System.out.println("📌 Marked as read: " + messageId);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean updateMessageArchiveStatus(String messageId, String username, boolean archive) {
        boolean found = false;
        String userKey = username.toLowerCase();

        String[] folders = {userKey, userKey + "_sent"};

        for (String folder : folders) {
            List<Message> messages = userMessages.get(folder);
            if (messages != null) {
                for (Message msg : messages) {
                    if (msg.getMessageId().equals(messageId)) {
                        msg.setArchived(archive);
                        found = true;
                        System.out.println((archive ? "🗑️ Archived" : "🔄 Restored") +
                                ": " + messageId);
                    }
                }
            }
        }

        if (found) {
            saveMessages();
        }

        return found;
    }

    public int getUnreadCount(String username) {
        List<Message> inbox = userMessages.getOrDefault(username.toLowerCase(), new ArrayList<>());
        return (int) inbox.stream()
                .filter(m -> !m.isRead() && !m.isArchived())
                .count();
    }

    public int getStorageUsed(String username) {
        int total = 0;
        String userKey = username.toLowerCase();

        List<Message> inbox = userMessages.getOrDefault(userKey, new ArrayList<>());
        for (Message msg : inbox) {
            total += msg.getBody().length();
        }

        List<Message> sent = userMessages.getOrDefault(userKey + "_sent", new ArrayList<>());
        for (Message msg : sent) {
            total += msg.getBody().length();
        }

        return total;
    }

    public int cleanupOldMessages(int days) {
        long cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        int removed = 0;

        for (List<Message> messages : userMessages.values()) {
            Iterator<Message> iterator = messages.iterator();
            while (iterator.hasNext()) {
                Message msg = iterator.next();
                if (msg.isArchived() && msg.getTimestamp() < cutoff) {
                    iterator.remove();
                    removed++;
                }
            }
        }

        if (removed > 0) {
            saveMessages();
            System.out.println("🧹 Cleaned " + removed + " old archived messages (older than " + days + " days)");
        }

        return removed;
    }

    public Map<String, Integer> getSendSummaries() {
        return new HashMap<>(sendCounts);
    }

    public Map<String, Integer> getListCounts() {
        return new HashMap<>(listCounts);
    }

    public Map<String, Integer> getRetrCounts() {
        return new HashMap<>(retrCounts);
    }

    @SuppressWarnings("unchecked")
    private void loadMessages() {
        File file = new File(MESSAGES_FILE);
        File parentDir = file.getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (!file.exists()) {
            System.out.println("📝 No existing messages file - creating fresh database");
            createSampleData();
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, List<Message>> loaded = (Map<String, List<Message>>) ois.readObject();
            userMessages.clear();
            userMessages.putAll(loaded);

            long maxId = userMessages.values().stream()
                    .flatMap(List::stream)
                    .mapToLong(msg -> {
                        try {
                            String[] parts = msg.getMessageId().split("_");
                            if (parts.length >= 3) {
                                return Long.parseLong(parts[2]);
                            }
                        } catch (Exception e) { }
                        return 0L;
                    })
                    .max()
                    .orElse(0L);

            nextId.set(maxId + 1);
            System.out.println("✅ Loaded " + countAllMessages() + " messages for " +
                    userMessages.size() + " folders");

        } catch (Exception e) {
            System.out.println("❌ Failed to load messages: " + e.getMessage());
            createSampleData();
        }
    }

    private void createSampleData() {
        System.out.println("📝 Creating sample messages...");

        saveMessage("admin", "user1", "Welcome to MailLite",
                "Hello user1! Welcome to our mail system.");
        saveMessage("user1", "admin", "Thank you",
                "Thanks for the welcome message!");
        saveMessage("admin", "user1,user2", "System Update",
                "There will be a system update tonight at 2 AM.");
        saveMessage("user2", "admin", "Question",
                "When will the maintenance be completed?");

        System.out.println("✅ Sample messages created");
    }

    private int countAllMessages() {
        return userMessages.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    private void saveMessages() {
        try {
            File file = new File(MESSAGES_FILE);
            File parentDir = file.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(userMessages);
                System.out.println("💾 Saved " + countAllMessages() + " messages to disk");
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to save messages: " + e.getMessage());
        }
    }

    public void printAllMessages() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📦 ALL MESSAGES IN DATABASE");
        System.out.println("=".repeat(60));

        for (Map.Entry<String, List<Message>> entry : userMessages.entrySet()) {
            System.out.println("\n📁 Folder: " + entry.getKey() +
                    " (" + entry.getValue().size() + " messages)");

            for (Message msg : entry.getValue()) {
                System.out.println("   📧 ID: " + msg.getMessageId() +
                        " | From: " + msg.getFrom() +
                        " | To: " + msg.getToAsString() +
                        " | Subject: " + msg.getSubject() +
                        " | Archived: " + (msg.isArchived() ? "✅" : "❌") +
                        " | Read: " + (msg.isRead() ? "✅" : "❌"));
            }
        }
        System.out.println("=".repeat(60) + "\n");
    }
    public int getTotalMessagesCount() {
        return countAllMessages();
    }


}