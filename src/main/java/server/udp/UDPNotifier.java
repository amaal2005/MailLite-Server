package server.udp;

import server.storage.SessionManager;
import server.storage.MessageManager;
import java.net.*;

public class UDPNotifier extends Thread {
    private DatagramSocket udpSocket;
    private int udpPort;
    private boolean running;
    private SessionManager sessionManager;
    private MessageManager messageManager;

    public UDPNotifier(int udpPort, SessionManager sessionManager, MessageManager messageManager) {
        this.udpPort = udpPort;
        this.sessionManager = sessionManager;
        this.messageManager = messageManager;
        this.running = false;
    }

    @Override
    public void run() {
        try {
            udpSocket = new DatagramSocket(udpPort);
            udpSocket.setReuseAddress(true);
            running = true;

            System.out.println("✅ UDP Notifier started on port " + udpPort);

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running) {
                try {
                    udpSocket.setSoTimeout(1000);
                    udpSocket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength()).trim();
                    InetAddress clientAddress = packet.getAddress();
                    int clientPort = packet.getPort();

                    handleUDPMessage(received, clientAddress, clientPort);

                } catch (SocketTimeoutException e) {
                    continue;
                } catch (Exception e) {
                    if (running) {
                        System.err.println("❌ UDP error: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("❌ Failed to start UDP notifier: " + e.getMessage());
        }
    }

    private void handleUDPMessage(String message, InetAddress address, int port) {
        System.out.println("📨 UDP received: " + message + " from " + address + ":" + port);
    }

    public void sendNotification(String username, int unreadCount) {
        try {
            var session = sessionManager.getSession(username);
            if (session != null && session.getUdpPort() > 0) {
                String notification = "NOTIFY NEWMAIL " + username + " " + unreadCount;
                byte[] data = notification.getBytes();

                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        session.getIpAddress(),
                        session.getUdpPort()
                );
                udpSocket.send(packet);
                System.out.println("📢 UDP notification sent to " + username + " (unread: " + unreadCount + ")");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to send UDP notification: " + e.getMessage());
        }
    }

    public void broadcastStatus(String username, String status) {
        try {
            String broadcastMsg = "NOTIFY STATUS " + username + " " + status;
            byte[] data = broadcastMsg.getBytes();

            var sessions = sessionManager.getAllActiveSessions();
            for (var session : sessions) {
                if (!session.getUsername().equals(username) && session.getUdpPort() > 0) {
                    DatagramPacket packet = new DatagramPacket(
                            data, data.length,
                            session.getIpAddress(),
                            session.getUdpPort()
                    );
                    udpSocket.send(packet);
                }
            }
            System.out.println("📢 Status broadcast: " + username + " -> " + status);
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast status: " + e.getMessage());
        }
    }

    public void broadcastOnlineList() {
        try {
            var sessions = sessionManager.getAllActiveSessions();
            StringBuilder usersList = new StringBuilder("ONLINE_USERS ");

            for (var session : sessions) {
                usersList.append(session.getUsername())
                        .append(",")
                        .append(session.getStatus())
                        .append(",")
                        .append(session.getIpAddress().getHostAddress())
                        .append(";");
            }

            String broadcastMsg = usersList.toString();
            byte[] data = broadcastMsg.getBytes();

            for (var session : sessions) {
                if (session.getUdpPort() > 0) {
                    DatagramPacket packet = new DatagramPacket(
                            data, data.length,
                            session.getIpAddress(),
                            session.getUdpPort()
                    );
                    udpSocket.send(packet);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast online list: " + e.getMessage());
        }
    }

    public void stopNotifier() {
        running = false;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        System.out.println("🛑 UDP Notifier stopped");
    }

    public boolean isRunning() {
        return running;
    }
}