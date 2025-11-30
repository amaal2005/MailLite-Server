// server/storage/SessionManager.java
package server.storage;

import server.models.UserSession;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private Map<String, UserSession> activeSessions;

    public SessionManager() {
        this.activeSessions = new ConcurrentHashMap<>();
        System.out.println("SessionManager initialized");
    }

    public UserSession createSession(String username, InetAddress ipAddress, int udpPort) {
        if (activeSessions.containsKey(username)) {
            System.out.println("Replacing existing session for: " + username);
            removeSession(username);
        }

        UserSession session = new UserSession(username, ipAddress, udpPort);
        activeSessions.put(username, session);
        System.out.println("Created session for: " + username + " from " + ipAddress.getHostAddress() + " UDP:" + udpPort);

        printOnlineUsers();
        return session;
    }

    public void removeSession(String username) {
        UserSession removed = activeSessions.remove(username);
        if (removed != null) {
            System.out.println("Removed session for: " + username);
            printOnlineUsers();
        } else {
            System.out.println("No session found to remove for: " + username);
        }
    }

    public UserSession getSession(String username) {
        UserSession session = activeSessions.get(username);
        if (session != null) {
            session.updateActivity();
        }
        return session;
    }

    public void updateUserStatus(String username, String status) {
        UserSession session = activeSessions.get(username);
        if (session != null) {
            String oldStatus = session.getStatus();
            session.setStatus(status);
            session.updateActivity();
            System.out.println("Updated status for " + username + " from " + oldStatus + " to: " + status);
        }
    }

    public List<String> getOnlineUsers() {
        List<String> onlineUsers = new ArrayList<>();
        System.out.println("Getting online users - Total sessions: " + activeSessions.size());

        for (UserSession session : activeSessions.values()) {
            System.out.println("   Session: " + session.getUsername() +
                    " - Authenticated: " + session.isAuthenticated() +
                    " - Status: " + session.getStatus() +
                    " - UDP Port: " + session.getUdpPort());

            if (session.isAuthenticated()) {
                String userInfo = session.getUsername() + " " +
                        session.getStatus() + " " +
                        session.getIpAddress().getHostAddress() + " " +
                        session.getUdpPort() + " " +
                        session.getLoginTime().getTime();
                onlineUsers.add(userInfo);
                System.out.println("   Added to online: " + session.getUsername() + " (UDP:" + session.getUdpPort() + ")");
            }
        }

        System.out.println("Online users count: " + onlineUsers.size());
        return onlineUsers;
    }

    public UserSession findRecipientSession(String recipient) {
        UserSession session = activeSessions.get(recipient);
        if (session != null && session.isAuthenticated()) {
            System.out.println("Recipient " + recipient + " is online and authenticated (UDP:" + session.getUdpPort() + ")");
            return session;
        } else {
            System.out.println("Recipient " + recipient + " not found or not authenticated");
            return null;
        }
    }

    public List<UserSession> getAllActiveSessions() {
        List<UserSession> active = new ArrayList<>();
        for (UserSession session : activeSessions.values()) {
            if (session.isAuthenticated()) {
                active.add(session);
            }
        }
        return active;
    }

    public boolean isUserOnlineWithUDP(String username) {
        UserSession session = activeSessions.get(username);
        boolean online = session != null && session.isAuthenticated() && session.getUdpPort() > 0;
        System.out.println("User " + username + " online with UDP: " + online);
        return online;
    }

    private void printOnlineUsers() {
        System.out.println("=== CURRENT ONLINE USERS ===");
        int authenticatedCount = 0;

        for (UserSession session : activeSessions.values()) {
            String authStatus = session.isAuthenticated() ? "AUTH" : "NOT AUTH";
            String udpInfo = session.getUdpPort() > 0 ? "UDP:" + session.getUdpPort() : "NO_UDP";
            System.out.println("   " + session.getUsername() +
                    " - " + authStatus +
                    " - " + session.getStatus() +
                    " - " + udpInfo +
                    " - IP: " + session.getIpAddress().getHostAddress());

            if (session.isAuthenticated()) {
                authenticatedCount++;
            }
        }

        System.out.println("Total: " + activeSessions.size() + " sessions, " +
                authenticatedCount + " authenticated users");
        System.out.println("=================================");
    }

    public int getOnlineCount() {
        int count = (int) activeSessions.values().stream()
                .filter(UserSession::isAuthenticated)
                .count();
        return count;
    }

    public void cleanupInactiveSessions() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, UserSession>> iterator = activeSessions.entrySet().iterator();
        int removed = 0;

        while (iterator.hasNext()) {
            Map.Entry<String, UserSession> entry = iterator.next();
            UserSession session = entry.getValue();
            long inactiveTime = currentTime - session.getLastActivity().getTime();

            if (inactiveTime > 30 * 60 * 1000) {
                System.out.println("Removing inactive session: " + session.getUsername());
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            System.out.println("Cleaned up " + removed + " inactive sessions");
        }
    }

    public boolean isUserOnline(String username) {
        UserSession session = activeSessions.get(username);
        boolean online = session != null && session.isAuthenticated();
        return online;
    }

    public Map<String, String> getSessionInfo() {
        Map<String, String> sessionInfo = new HashMap<>();
        for (UserSession session : activeSessions.values()) {
            String info = String.format("IP: %s, Status: %s, Authenticated: %s, UDP: %d, LastActive: %s",
                    session.getIpAddress().getHostAddress(),
                    session.getStatus(),
                    session.isAuthenticated(),
                    session.getUdpPort(),
                    new Date(session.getLastActivity().getTime()));
            sessionInfo.put(session.getUsername(), info);
        }
        return sessionInfo;
    }

    public void reset() {
        int count = activeSessions.size();
        activeSessions.clear();
        System.out.println("Reset all " + count + " sessions");
    }

    public int getTotalSessions() {
        return activeSessions.size();
    }

    public boolean hasSession(String username) {
        return activeSessions.containsKey(username);
    }
}