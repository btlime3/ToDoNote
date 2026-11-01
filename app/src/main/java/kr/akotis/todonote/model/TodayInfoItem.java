package kr.akotis.todonote.model;

/** 오늘의 정보 화면에 표시하는 항목 하나. */
public class TodayInfoItem {
    private final String category;
    private final String title;
    private final String summary;

    public TodayInfoItem(String category, String title, String summary) {
        this.category = category;
        this.title = title;
        this.summary = summary;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }
}
