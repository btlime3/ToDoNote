package kr.akotis.todonote.model;

/** 노트를 수정하기 전에 보관해 둔 이전 버전. */
public class NoteVersion {
    private final long id;
    private final long noteId;
    private final String title;
    private final String content;
    private final long savedAt;

    public NoteVersion(long id, long noteId, String title, String content, long savedAt) {
        this.id = id;
        this.noteId = noteId;
        this.title = title;
        this.content = content;
        this.savedAt = savedAt;
    }

    public long getId() {
        return id;
    }

    public long getNoteId() {
        return noteId;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public String getContent() {
        return content != null ? content : "";
    }

    /** 이 내용이 마지막으로 저장되었던 시각. */
    public long getSavedAt() {
        return savedAt;
    }
}
