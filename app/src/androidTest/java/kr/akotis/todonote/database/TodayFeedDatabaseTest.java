package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

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
        return new TodayItem(url, category, "출처", "제목 " + url, "요약", publishedAt);
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
        assertEquals("security", items.get(0).getCategory());
        assertEquals(1_700_000_000_000L, items.get(0).getPublishedAt());
        assertEquals(TodayItem.CATEGORY_NEWS, items.get(1).getCategory());
        assertEquals("미래 날짜는 쓰지 않는다", 0, items.get(1).getPublishedAt());
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

        TodayItem changed = new TodayItem("https://a", "ai", "출처", "바뀐 제목", "바뀐 요약", NOW - 1000);
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
