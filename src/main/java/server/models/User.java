// server/models/User.java
package server.models;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String status;
    private long lastLogin;
    private long lastSeen;

    public User() {}

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.status = "OFFLINE";
        this.lastLogin = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = status;
        this.lastSeen = System.currentTimeMillis();
    }

    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}