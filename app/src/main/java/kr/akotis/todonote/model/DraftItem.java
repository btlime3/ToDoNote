package kr.akotis.todonote.model;

/** 아직 저장하지 않은 편집 내용. 앱이 강제로 종료되어도 다시 불러올 수 있게 DB에 보관한다. */
public class DraftItem {
    private final long targetId;
    private final String title;
    private final String content;
    private final long savedAt;

    public DraftItem(long targetId, String title, String content, long savedAt) {
        this.targetId = targetId;
        this.title = title;
        this.content = content;
        this.savedAt = savedAt;
    }

    /** 편집 중이던 항목의 ID. 새 항목이면 {@code DatabaseHelper.DRAFT_NEW_ITEM}. */
    public long getTargetId() {
        return targetId;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public String getContent() {
        return content != null ? content : "";
    }

    public long getSavedAt() {
        return savedAt;
    }
}
