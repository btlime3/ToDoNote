package kr.akotis.todonote.model;

public class MessageItem {
    private long id;
    private String title;
    private String body;
    private long receivedAt;
    private boolean isRead;
    private long deletedAt;

    public MessageItem() {}

    public MessageItem(String title, String body) {
        this.title = title;
        this.body = body;
        this.receivedAt = System.currentTimeMillis();
        this.isRead = false;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(long deletedAt) {
        this.deletedAt = deletedAt;
    }
}

