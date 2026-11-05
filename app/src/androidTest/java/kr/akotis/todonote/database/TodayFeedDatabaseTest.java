package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import kr.akotis.todonote.model.TodayItem;
import kr.akotis.todonote.util.TodayFeedParser;

@RunWith(AndroidJUnit4.class)
public class TodayFeedDatabaseTest {
    private static final long NOW = 1_800_000_000_000L;

    private Context context;
    private String databaseName;
    private TodayFeedDatabase database;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "today_feed_test_" + UUID.randomUUID() + ".db";
        database = new TodayFeedDatabase(context, databaseName);
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(databaseName);
    }

    private static TodayItem item(String url, String category, long publishedAt) {
        return new TodayItem(url, category, "출처", "제목 " + url, "요약", "", publishedAt);
    }

    @Test
    public void parserKeepsValidItemsAndSkipsBadOnes() {
        String json = "["
                + "{\"c\":\"security\",\"s\":\"CISA\",\"t\":\"제목\",\"d\":\"요약\",\"u\":\"https://example.com/a\",\"p\":1700000000},"
                + "{\"t\":\"주소 없음\"},"
                + "{\"t\":\"위험한 주소\",\"u\":\"javascript:alert(1)\"},"
                + "{\"t\":\"파일 주소\",\"u\":\"file:///etc/hosts\"},"
                + "{\"u\":\"https://example.com/no-title\"},"
                + "\"문자열 항목\","
                + "{\"t\":\"분류 없음\",\"u\":\"http://example.com/b\",\"p\":99999999999}"
                + "]";

        List<TodayItem> items = TodayFeedParser.parse(json, NOW);

        assertEquals(2, items.size());
        assertEquals(TodayItem.CATEGORY_NEWS, items.get(0).getCategory());
        assertEquals(1_700_000_000_000L, items.get(0).getPublishedAt());
        assertEquals(TodayItem.CATEGORY_NEWS, items.get(1).getCategory());
        assertEquals("미래 날짜는 쓰지 않는다", 0, items.get(1).getPublishedAt());
    }

    @Test
    public void parserMapsCategoriesToTwoTabsAndKeepsOnlyHttpsImages() {
        String json = "[{\"c\":\"tech\",\"t\":\"1\",\"u\":\"https://e.com/1\",\"i\":\"https://e.com/1.jpg\"},"
                + "{\"c\":\"research\",\"t\":\"2\",\"u\":\"https://e.com/2\",\"i\":\"http://e.com/2.jpg\"},"
                + "{\"c\":\"opensource\",\"t\":\"3\",\"u\":\"https://e.com/3\"},"
                + "{\"c\":\"security\",\"t\":\"4\",\"u\":\"https://e.com/4\"}]";

        List<TodayItem> items = TodayFeedParser.parse(json, NOW);

        assertEquals(TodayItem.CATEGORY_TECH, items.get(0).getCategory());
        assertEquals("https://e.com/1.jpg", items.get(0).getImageUrl());
        assertEquals(TodayItem.CATEGORY_TECH, items.get(1).getCategory());
        assertEquals("", items.get(1).getImageUrl());
        assertEquals(TodayItem.CATEGORY_TECH, items.get(2).getCategory());
        assertEquals(TodayItem.CATEGORY_NEWS, items.get(3).getCategory());
    }

    @Test
    public void upgradeFromVersion1KeepsItemsAndMergesCategories() {
        database.close();
        context.deleteDatabase(databaseName);
        try (SQLiteDatabase old = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)) {
            old.execSQL("CREATE TABLE today_item (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, "
                    + "category TEXT NOT NULL, source TEXT, title TEXT NOT NULL, summary TEXT, "
                    + "published_at INTEGER NOT NULL DEFAULT 0, received_at INTEGER NOT NULL, is_read INTEGER NOT NULL DEFAULT 0)");
            old.execSQL("INSERT INTO today_item (url, category, title, received_at, is_read) VALUES "
                    + "('https://a', 'research', 'A', 1, 1), ('https://b', 'security', 'B', 1, 0), ('https://c', 'ai', 'C', 1, 0)");
            old.setVersion(1);
        }

        database = new TodayFeedDatabase(context, databaseName);

        List<TodayItem> tech = database.getItems(TodayItem.CATEGORY_TECH);
        assertEquals(1, tech.size());
        assertTrue("읽음 표시가 유지된다", tech.get(0).isRead());
        assertNull(tech.get(0).getImageUrl());
        assertFalse(tech.get(0).isAiSummary());
        assertEquals(2, database.getItems(TodayItem.CATEGORY_NEWS).size());
    }

    @Test
    public void aiSummaryFlagIsParsedAndStored() {
        String json = "[{\"c\":\"news\",\"t\":\"제목\",\"d\":\"요약\",\"u\":\"https://e.com/1\",\"a\":1},"
                + "{\"c\":\"news\",\"t\":\"제목 2\",\"u\":\"https://e.com/2\"}]";

        database.save(TodayFeedParser.parse(json, NOW), NOW);

        List<TodayItem> items = database.getItems(null);
        assertEquals(1, items.stream().filter(TodayItem::isAiSummary).count());
    }

    @Test
    public void aiTextIsStoredApartFromSummaryAndShownOnlyForTech() {
        String json = "[{\"c\":\"tech\",\"t\":\"기술\",\"d\":\"원문\",\"u\":\"https://e.com/t\",\"ai\":\"AI 요약\"},"
                + "{\"c\":\"news\",\"t\":\"뉴스\",\"d\":\"원문\",\"u\":\"https://e.com/n\",\"ai\":\"AI 요약\"}]";

        database.save(TodayFeedParser.parse(json, NOW), NOW);

        TodayItem tech = database.getItems(TodayItem.CATEGORY_TECH).get(0);
        assertEquals("원문", tech.getDisplaySummary());
        assertEquals("AI 요약", tech.getDisplayAiText());
        TodayItem news = database.getItems(TodayItem.CATEGORY_NEWS).get(0);
        assertEquals("원문", news.getDisplaySummary());
        assertEquals("", news.getDisplayAiText());
    }

    /** 1.15까지의 수집기가 보낸 글은 요약 칸에 AI 요약이 있다. */
    @Test
    public void oldAiSummaryMovesToTheBoxOnlyForTech() {
        String json = "[{\"c\":\"tech\",\"t\":\"기술\",\"d\":\"옛 AI 요약\",\"u\":\"https://e.com/t\",\"a\":1},"
                + "{\"c\":\"news\",\"t\":\"뉴스\",\"d\":\"옛 AI 요약\",\"u\":\"https://e.com/n\",\"a\":1}]";

        database.save(TodayFeedParser.parse(json, NOW), NOW);

        TodayItem tech = database.getItems(TodayItem.CATEGORY_TECH).get(0);
        assertEquals("", tech.getDisplaySummary());
        assertEquals("옛 AI 요약", tech.getDisplayAiText());
        TodayItem news = database.getItems(TodayItem.CATEGORY_NEWS).get(0);
        assertEquals("옛 AI 요약", news.getDisplaySummary());
        assertEquals("", news.getDisplayAiText());
    }

    @Test
    public void resendingWithoutAiTextKeepsExistingAiText() {
        String withAi = "[{\"c\":\"tech\",\"t\":\"기술\",\"d\":\"원문\",\"u\":\"https://e.com/t\",\"ai\":\"AI 요약\"}]";
        String withoutAi = "[{\"c\":\"tech\",\"t\":\"기술\",\"d\":\"원문\",\"u\":\"https://e.com/t\"}]";

        database.save(TodayFeedParser.parse(withAi, NOW), NOW);
        database.save(TodayFeedParser.parse(withoutAi, NOW), NOW);

        assertEquals("AI 요약", database.getItems(TodayItem.CATEGORY_TECH).get(0).getDisplayAiText());
    }

    /** 읽음 표시와 소식이 그대로 남은 채로 새 컬럼만 추가되어야 한다. */
    @Test
    public void upgradeFromVersion3KeepsItems() {
        database.close();
        context.deleteDatabase(databaseName);
        try (SQLiteDatabase old = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)) {
            old.execSQL("CREATE TABLE today_item (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, "
                    + "category TEXT NOT NULL, source TEXT, title TEXT NOT NULL, summary TEXT, image_url TEXT, "
                    + "published_at INTEGER NOT NULL DEFAULT 0, received_at INTEGER NOT NULL, "
                    + "is_read INTEGER NOT NULL DEFAULT 0, ai_summary INTEGER NOT NULL DEFAULT 0)");
            old.execSQL("INSERT INTO today_item (url, category, title, summary, received_at, is_read, ai_summary) VALUES "
                    + "('https://a', 'tech', 'A', '옛 AI 요약', 1, 1, 1)");
            old.setVersion(3);
        }

        database = new TodayFeedDatabase(context, databaseName);

        TodayItem item = database.getItems(TodayItem.CATEGORY_TECH).get(0);
        assertTrue(item.isRead());
        assertEquals("옛 AI 요약", item.getDisplayAiText());
    }

    @Test
    public void eachTabKeepsAtMostFiftyItems() {
        assertEquals(50, TodayFeedDatabase.MAX_ITEMS_PER_CATEGORY);
    }

    @Test
    public void resendingWithoutImageKeepsExistingThumbnail() {
        database.save(Collections.singletonList(new TodayItem("https://a", "news", "s", "t", "d", "https://e.com/a.jpg", NOW)), NOW);
        database.save(Collections.singletonList(new TodayItem("https://a", "news", "s", "t", "d", "", NOW)), NOW);
        assertEquals("https://e.com/a.jpg", database.getItems(null).get(0).getImageUrl());
    }

    @Test
    public void parserReturnsEmptyListForBrokenInput() {
        assertTrue(TodayFeedParser.parse(null, NOW).isEmpty());
        assertTrue(TodayFeedParser.parse("JSON 아님", NOW).isEmpty());
        assertTrue(TodayFeedParser.parse("{\"t\":\"배열 아님\"}", NOW).isEmpty());
    }

    @Test
    public void parserTreatsJsonNullAsMissing() {
        String json = "[{\"t\":null,\"u\":\"https://example.com/a\"},"
                + "{\"t\":\"제목\",\"u\":\"https://example.com/b\",\"c\":null,\"s\":null,\"d\":null,\"p\":null}]";

        List<TodayItem> items = TodayFeedParser.parse(json, NOW);

        assertEquals(1, items.size());
        assertEquals(TodayItem.CATEGORY_NEWS, items.get(0).getCategory());
        assertEquals("", items.get(0).getSource());
        assertEquals("", items.get(0).getSummary());
    }

    @Test
    public void parserIgnoresFutureDates() {
        String json = "[{\"t\":\"제목\",\"u\":\"https://example.com/a\",\"p\":" + (NOW / 1000 + 3600) + "}]";
        assertEquals(0, TodayFeedParser.parse(json, NOW).get(0).getPublishedAt());
    }

    @Test
    public void parserClipsLongText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append('가');
        }
        String json = "[{\"t\":\"" + longText + "\",\"d\":\"" + longText + "\",\"s\":\"" + longText
                + "\",\"u\":\"https://example.com/long\"}]";

        TodayItem item = TodayFeedParser.parse(json, NOW).get(0);

        assertEquals(300, item.getTitle().length());
        assertEquals(600, item.getSummary().length());
        assertEquals(40, item.getSource().length());
    }

    @Test
    public void sameUrlUpdatesContentButKeepsReadStateAndFirstReceivedTime() {
        assertEquals(1, database.save(Collections.singletonList(item("https://a", "ai", NOW - 1000)), NOW));
        TodayItem saved = database.getItems(null).get(0);
        database.markRead(saved.getId());

        TodayItem changed = new TodayItem("https://a", "ai", "출처", "바뀐 제목", "바뀐 요약", "", NOW - 1000);
        assertEquals("새 항목이 아니다", 0, database.save(Collections.singletonList(changed), NOW + 5000));

        List<TodayItem> items = database.getItems(null);
        assertEquals(1, items.size());
        assertEquals("바뀐 제목", items.get(0).getTitle());
        assertTrue(items.get(0).isRead());
        assertEquals(NOW, items.get(0).getReceivedAt());
        assertEquals(NOW, database.getLastReceivedAt());
    }

    @Test
    public void itemsAreNewestFirstAndFilteredByCategory() {
        List<TodayItem> batch = new ArrayList<>();
        batch.add(item("https://old", "ai", NOW - 3000));
        batch.add(item("https://new", "security", NOW - 1000));
        batch.add(item("https://undated", "ai", 0));
        database.save(batch, NOW);

        List<TodayItem> all = database.getItems(null);
        assertEquals("날짜가 없으면 받은 시각으로 정렬한다", "https://undated", all.get(0).getUrl());
        assertEquals("https://new", all.get(1).getUrl());
        assertEquals("https://old", all.get(2).getUrl());

        List<TodayItem> ai = database.getItems("ai");
        assertEquals(2, ai.size());
        assertFalse(ai.stream().anyMatch(i -> "security".equals(i.getCategory())));
    }

    @Test
    public void oldItemsAreRemovedAfterRetentionPeriod() {
        database.save(Collections.singletonList(item("https://old", "ai", NOW)), NOW);

        long later = NOW + TodayFeedDatabase.RETENTION_MILLIS + TimeUnit.DAYS.toMillis(1);
        database.save(Collections.singletonList(item("https://new", "ai", later)), later);

        List<TodayItem> items = database.getItems(null);
        assertEquals(1, items.size());
        assertEquals("https://new", items.get(0).getUrl());
    }

    @Test
    public void frequentNewsDoesNotPushOutRareResearch() {
        database.save(Collections.singletonList(item("https://research", "research", NOW - TimeUnit.DAYS.toMillis(10))), NOW);
        List<TodayItem> news = new ArrayList<>();
        for (int i = 0; i < TodayFeedDatabase.MAX_ITEMS_PER_CATEGORY + 20; i++) {
            news.add(item("https://example.com/" + i, "news", NOW));
        }
        database.save(news, NOW);

        assertEquals(1, database.getItems("research").size());
        assertEquals(TodayFeedDatabase.MAX_ITEMS_PER_CATEGORY, database.getItems("news").size());
    }

    @Test
    public void oldestReceivedItemsAreRemovedFirstInsideACategory() {
        List<TodayItem> batch = new ArrayList<>();
        for (int i = 0; i < TodayFeedDatabase.MAX_ITEMS_PER_CATEGORY + 20; i++) {
            batch.add(item("https://example.com/" + i, "news", NOW - 100000 + i));
        }
        database.save(batch, NOW);

        List<TodayItem> items = database.getItems("news");
        assertEquals(TodayFeedDatabase.MAX_ITEMS_PER_CATEGORY, items.size());
        assertEquals("https://example.com/" + (TodayFeedDatabase.MAX_ITEMS_PER_CATEGORY + 19), items.get(0).getUrl());
        assertEquals("https://example.com/20", items.get(items.size() - 1).getUrl());
    }
}
