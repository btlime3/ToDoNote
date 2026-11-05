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
    private String aiText;

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

    /** 1.15까지의 형식. 요약 칸의 글을 AI가 만들었는지를 뜻한다. 이미 저장된 글을 위해 남겨 둔다. */
    public boolean isAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(boolean aiSummary) {
        this.aiSummary = aiSummary;
    }

    /** @return 원문 요약과 따로 받은 AI 요약. 없으면 null 또는 빈 문자열 */
    public String getAiText() {
        return aiText;
    }

    public void setAiText(String aiText) {
        this.aiText = aiText;
    }

    /** 옛 형식의 AI 요약은 새로운 기술 탭에서만 상자로 옮긴다. 오늘 소식에는 AI 요약 상자를 두지 않는다. */
    private boolean summaryIsAiBox() {
        return aiSummary && CATEGORY_TECH.equals(category);
    }

    /** @return 카드 본문에 보여 줄 글. 없으면 null 또는 빈 문자열 */
    public String getDisplaySummary() {
        return summaryIsAiBox() ? "" : summary;
    }

    /** @return 카드 안의 상자에 보여 줄 AI 요약. 없으면 빈 문자열 */
    public String getDisplayAiText() {
        if (!CATEGORY_TECH.equals(category)) {
            return "";
        }
        if (aiText != null && !aiText.isEmpty()) {
            return aiText;
        }
        return summaryIsAiBox() && summary != null ? summary : "";
    }

    /** 발행 시각을 모르면 받은 시각을 쓴다. */
    public long getDisplayTime() {
        return publishedAt > 0 ? publishedAt : receivedAt;
    }
}
