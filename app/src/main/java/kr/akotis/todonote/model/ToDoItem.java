package kr.akotis.todonote.model;

public class ToDoItem {
    private long id;
    private String title;
    private String detail;
    private long date; // 날짜 (년월일만, 시간 제외)
    private long time; // 시간 (밀리초)
    private boolean isCompleted;
    private boolean isChecked; // 체크 상태 (UI용, 완료 여부와 무관)
    private long createdAt;
    private long updatedAt;

    public ToDoItem() {
    }

    public ToDoItem(String title, String detail, long date, long time) {
        this.title = title;
        this.detail = detail;
        this.date = date;
        this.time = time;
        this.isCompleted = false;
        this.isChecked = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
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

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

