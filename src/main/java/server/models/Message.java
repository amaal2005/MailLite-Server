// server/models/Message.java
// النسخة النهائية المثالية - مضمونة 100% للتسليم
package server.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String from;
    private List<String> to;        // قائمة المستلمين (لدعم أكثر من واحد)
    private String subject;
    private String body;
    private long timestamp;
    private boolean isRead = false;
    private boolean isArchived = false;

    // Constructor للرسائل العادية (Inbox)
    public Message(String messageId, String from, String to, String subject, String body, long timestamp) {
        this(messageId, from, List.of(to), subject, body, timestamp);
    }

    // Constructor للرسائل في Sent (قد يكون فيه أكثر من مستلم)
    public Message(String messageId, String from, List<String> to, String subject, String body, long timestamp) {
        this.messageId = messageId;
        this.from = from;
        this.to = new ArrayList<>(to);  // نسخة آمنة
        this.subject = subject;
        this.body = body;
        this.timestamp = timestamp;
    }

    // Getters
    public String getMessageId() { return messageId; }
    public String getFrom() { return from; }
    public List<String> getToList() { return new ArrayList<>(to); }  // نسخة آمنة

    // مهم جدًا: دالة ترجع المستلم كـ String (للعرض في الكلاينت)
    public String getToAsString() {
        if (to == null || to.isEmpty()) return "Unknown";
        return String.join(", ", to);
    }

    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public long getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public boolean isArchived() { return isArchived; }

    // Setters
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setFrom(String from) { this.from = from; }
    public void setToList(List<String> to) { this.to = new ArrayList<>(to); }
    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
    public void setRead(boolean read) { isRead = read; }
    public void setArchived(boolean archived) { isArchived = archived; }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + messageId + '\'' +
                ", from='" + from + '\'' +
                ", to=" + getToAsString() +
                ", subject='" + subject + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}