package kr.akotis.todonote.model;

/** 오늘 화면에 표시하는 소식 하나. 서버가 FCM으로 보낸다. */
public class TodayItem {
    public static final String CATEGORY_OPENSOURCE = "opensource";
    public static final String CATEGORY_RESEARCH = "research";
    public static final String CATEGORY_AI = "ai";
    public static final String CATEGORY_SECURITY = "security";
    public static final String CATEGORY_NEWS = "news";

    private long id;
    private final String url;
    private final String category;
    private final String source;
    private final String title;
    private final String summary;
    private final long publishedAt;
    private long receivedAt;
    private boolean read;

    public TodayItem(String url, String category, String source, String title, String summary, long publishedAt) {
        this.url = url;
        this.category = category;
        this.source = source;
        this.title = title;
        this.summary = summary;
        this.publishedAt = publishedAt;
    }

    /** 화면에 쓰는 분류 이름. 앱이 모르는 분류를 서버가 보내면 받은 값을 그대로 보여 준다. */
    public static String categoryLabel(String category) {
        if (CATEGORY_OPENSOURCE.equals(category)) {
            return "오픈소스";
        } else if (CATEGORY_RESEARCH.equals(category)) {
            return "연구";
        } else if (CATEGORY_AI.equals(category)) {
            return "AI";
        } else if (CATEGORY_SECURITY.equals(category)) {
            return "보안";
        } else if (CATEGORY_NEWS.equals(category)) {
            return "IT 뉴스";
        }
        return category;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public String getCategory() {
        return category;
    }

    public String getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    /** 발행 시각을 모르면 받은 시각을 쓴다. */
    public long getDisplayTime() {
        return publishedAt > 0 ? publishedAt : receivedAt;
    }
}
