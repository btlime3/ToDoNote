package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;

import kr.akotis.todonote.model.TodayItem;

@RunWith(AndroidJUnit4.class)
public class SavedArticleTest {
    private Context context;
    private String databaseName;
    private DatabaseHelper helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "saved_article_test_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, databaseName);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(databaseName);
    }

    private static TodayItem article(String url) {
        TodayItem item = new TodayItem(url, TodayItem.CATEGORY_TECH, "OpenAI", "제목", "요약", "https://e.com/a.jpg", 1000);
        item.setAiSummary(true);
        return item;
    }

    @Test
    public void savedArticleKeepsItsContentAndNewestComesFirst() {
        helper.saveArticle(article("https://e.com/old"), 100);
        helper.saveArticle(article("https://e.com/new"), 200);

        List<TodayItem> saved = helper.getSavedArticles();

        assertEquals(2, saved.size());
        assertEquals("https://e.com/new", saved.get(0).getUrl());
        assertEquals("제목", saved.get(0).getTitle());
        assertEquals("요약", saved.get(0).getSummary());
        assertEquals("https://e.com/a.jpg", saved.get(0).getImageUrl());
        assertEquals(1000, saved.get(0).getPublishedAt());
        assertEquals(200, saved.get(0).getReceivedAt());
        assertTrue(saved.get(0).isAiSummary());
    }

    @Test
    public void savingTwiceKeepsOneAndRemoveDeletesIt() {
        helper.saveArticle(article("https://e.com/a"), 100);
        helper.saveArticle(article("https://e.com/a"), 200);
        assertEquals(1, helper.getSavedArticles().size());
        assertEquals(100, helper.getSavedArticles().get(0).getReceivedAt());
        assertTrue(helper.getSavedArticleUrls().contains("https://e.com/a"));

        helper.removeSavedArticle("https://e.com/a");

        assertTrue(helper.getSavedArticles().isEmpty());
        assertFalse(helper.getSavedArticleUrls().contains("https://e.com/a"));
    }

    /** 1.13 이전의 v14 DB와 그 백업을 열어도 메모가 그대로이고 보관함을 쓸 수 있어야 한다. */
    @Test
    public void upgradeFromVersion14AddsTableAndKeepsData() {
        helper.getWritableDatabase().execSQL("INSERT INTO memo (content, memo_order) VALUES ('기존 메모', 1)");
        helper.getWritableDatabase().execSQL("DROP TABLE saved_article");
        helper.getWritableDatabase().setVersion(14);
        helper.close();

        helper = new DatabaseHelper(context, databaseName);
        helper.saveArticle(article("https://e.com/a"), 100);

        assertEquals(1, helper.getSavedArticles().size());
        SQLiteDatabase db = helper.getReadableDatabase();
        assertEquals(15, db.getVersion());
        try (android.database.Cursor cursor = db.rawQuery("SELECT content FROM memo", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("기존 메모", cursor.getString(0));
        }
    }
}
