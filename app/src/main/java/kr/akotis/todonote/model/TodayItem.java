package kr.akotis.todonote.model;

/** 오늘 화면에 표시하는 소식 하나. 서버가 FCM으로 보낸다. */
public class TodayItem {
    /** 오늘 소식 탭: IT, AI, 보안 뉴스 */
    public static final String CATEGORY_NEWS = "news";
    /** 새로운 기술 탭: 연구소 발표, 논문, 새 모델과 오픈소스 */
    public static final String CATEGORY_TECH = "tech";

    private long id;
    private final String url;
    private final String category;
    private final String source;
    private final String title;
    private final String summary;
    private final String imageUrl;
    private final long publishedAt;
    private long receivedAt;
    private boolean read;
    private boolean aiSummary;

    public TodayItem(String url, String category, String source, String title, String summary,
                     String imageUrl, long publishedAt) {
        this.url = url;
        this.category = category;
        this.source = source;
        this.title = title;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
    }

    /** 두 탭 중 하나로 정한다. 1.11의 수집기가 보내는 다섯 분류도 받을 수 있다. */
    public static String normalizeCategory(String category) {
        boolean tech = CATEGORY_TECH.equals(category) || "research".equals(category) || "opensource".equals(category);
        return tech ? CATEGORY_TECH : CATEGORY_NEWS;
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

    /** @return 썸네일 주소. 없으면 null 또는 빈 문자열 */
    public String getImageUrl() {
        return imageUrl;
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

    /** 요약을 AI가 만들었는지. 카드에 표시해서 원문과 구분한다. */
    public boolean isAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(boolean aiSummary) {
        this.aiSummary = aiSummary;
    }

    /** 발행 시각을 모르면 받은 시각을 쓴다. */
    public long getDisplayTime() {
        return publishedAt > 0 ? publishedAt : receivedAt;
    }
}
