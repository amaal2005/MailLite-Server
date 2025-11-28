package server.models;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Date;

public class UserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private transient InetAddress ipAddress; // transient علشان ما يتسلسلش
    private int udpPort;
    private boolean authenticated;
    private String status;
    private Date loginTime;
    private Date lastActivity;

    public UserSession(String username, InetAddress ipAddress, int udpPort) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.udpPort = udpPort;
        this.authenticated = false;
        this.status = "ACTIVE";
        this.loginTime = new Date();
        this.lastActivity = new Date();
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public InetAddress getIpAddress() { return ipAddress; }
    public void setIpAddress(InetAddress ipAddress) { this.ipAddress = ipAddress; }

    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getLoginTime() { return loginTime; }
    public void setLoginTime(Date loginTime) { this.loginTime = loginTime; }

    public Date getLastActivity() { return lastActivity; }
    public void setLastActivity(Date lastActivity) { this.lastActivity = lastActivity; }

    public void updateActivity() {
        this.lastActivity = new Date();
    }
}