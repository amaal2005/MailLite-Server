// server/utils/Config.java
package server.utils;

public class Config {
    public static final int TCP_PORT = 1234;
    public static final int UDP_PORT = 1235;
    public static final int MAX_MESSAGE_SIZE = 64 * 1024; // 64KB
    public static final int CLEANUP_DAYS = 30; // Clean messages older than 30 days
}