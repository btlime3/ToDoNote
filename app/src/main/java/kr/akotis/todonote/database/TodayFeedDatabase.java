package kr.akotis.todonote.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kr.akotis.todonote.model.TodayItem;

/**
 * 오늘 화면의 소식을 보관한다. 메모와 노트가 있는 ToDoNote.db와 파일을 나눠
 * 내보내기와 가져오기에 영향을 주지 않는다.
 */
public class TodayFeedDatabase extends SQLiteOpenHelper {
    public static final int MAX_ITEMS_PER_CATEGORY = 200;
    public static final long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(14);

    private static final String DATABASE_NAME = "TodayFeed.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE = "today_item";
    private static final String COL_ID = "id";
    private static final String COL_URL = "url";
    private static final String COL_CATEGORY = "category";
    private static final String COL_SOURCE = "source";
    private static final String COL_TITLE = "title";
    private static final String COL_SUMMARY = "summary";
    private static final String COL_IMAGE_URL = "image_url";
    private static final String COL_PUBLISHED_AT = "published_at";
    private static final String COL_RECEIVED_AT = "received_at";
    private static final String COL_READ = "is_read";

    /** 발행 시각을 모르는 항목은 받은 시각으로 정렬한다. */
    private static final String NEWEST_FIRST =
            "CASE WHEN " + COL_PUBLISHED_AT + " > 0 THEN " + COL_PUBLISHED_AT + " ELSE " + COL_RECEIVED_AT + " END DESC, "
                    + COL_ID + " DESC";

    private static TodayFeedDatabase instance;

    /** FCM 수신과 화면이 같은 연결을 쓰도록 하나만 만든다. 닫지 않고 프로세스와 함께 사라진다. */
    public static synchronized TodayFeedDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new TodayFeedDatabase(context.getApplicationContext(), DATABASE_NAME);
        }
        return instance;
    }

    TodayFeedDatabase(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_URL + " TEXT NOT NULL UNIQUE, " +
                COL_CATEGORY + " TEXT NOT NULL, " +
                COL_SOURCE + " TEXT, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_SUMMARY + " TEXT, " +
                COL_IMAGE_URL + " TEXT, " +
                COL_PUBLISHED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                COL_RECEIVED_AT + " INTEGER NOT NULL, " +
                COL_READ + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_IMAGE_URL + " TEXT");
            // 다섯 분류를 두 탭으로 합친다.
            db.execSQL("UPDATE " + TABLE + " SET " + COL_CATEGORY + " = '" + TodayItem.CATEGORY_TECH + "'" +
                    " WHERE " + COL_CATEGORY + " IN ('research', 'opensource')");
            db.execSQL("UPDATE " + TABLE + " SET " + COL_CATEGORY + " = '" + TodayItem.CATEGORY_NEWS + "'" +
                    " WHERE " + COL_CATEGORY + " <> '" + TodayItem.CATEGORY_TECH + "'");
        }
    }

    /**
     * 같은 주소를 다시 받으면 내용만 갱신하고 읽음 표시와 처음 받은 시각은 유지한다.
     * 저장 뒤 오래된 항목을 지운다.
     * @return 새로 추가된 항목 수
     */
    public int save(List<TodayItem> items, long now) {
        SQLiteDatabase db = getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try {
            for (TodayItem item : items) {
                ContentValues values = new ContentValues();
                values.put(COL_CATEGORY, item.getCategory());
                values.put(COL_SOURCE, item.getSource());
                values.put(COL_TITLE, item.getTitle());
                values.put(COL_SUMMARY, item.getSummary());
                values.put(COL_PUBLISHED_AT, item.getPublishedAt());
                // 다시 받을 때 이미지를 찾지 못했어도 이미 있는 썸네일은 지우지 않는다.
                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    values.put(COL_IMAGE_URL, item.getImageUrl());
                }
                int updated = db.update(TABLE, values, COL_URL + " = ?", new String[]{item.getUrl()});
                if (updated == 0) {
                    values.put(COL_URL, item.getUrl());
                    values.put(COL_RECEIVED_AT, now);
                    db.insertOrThrow(TABLE, null, values);
                    added++;
                }
            }
            prune(db, now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return added;
    }

    /** 앱이 차지하는 공간이 계속 늘지 않도록 기간과 개수를 함께 제한한다. */
    private static void prune(SQLiteDatabase db, long now) {
        db.delete(TABLE, COL_RECEIVED_AT + " < ?", new String[]{String.valueOf(now - RETENTION_MILLIS)});
        // 분류마다 받은 순서로 센다. 전체를 한 번에 세면 글이 잦은 뉴스가 드문 연구 소식을 밀어낸다.
        db.delete(TABLE, COL_ID + " IN (SELECT " + COL_ID + " FROM (SELECT " + COL_ID +
                ", ROW_NUMBER() OVER (PARTITION BY " + COL_CATEGORY + " ORDER BY " + COL_ID + " DESC) AS position" +
                " FROM " + TABLE + ") WHERE position > " + MAX_ITEMS_PER_CATEGORY + ")", null);
    }

    /** @param category 분류. null이면 전체 */
    public List<TodayItem> getItems(String category) {
        String selection = category != null ? COL_CATEGORY + " = ?" : null;
        String[] args = category != null ? new String[]{category} : null;
        List<TodayItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, selection, args, null, null, NEWEST_FIRST)) {
            while (cursor.moveToNext()) {
                TodayItem item = new TodayItem(
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_URL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_SUMMARY)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_PUBLISHED_AT)));
                item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
                item.setReceivedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_RECEIVED_AT)));
                item.setRead(cursor.getInt(cursor.getColumnIndexOrThrow(COL_READ)) == 1);
                items.add(item);
            }
        }
        return items;
    }

    public void markRead(long id) {
        ContentValues values = new ContentValues();
        values.put(COL_READ, 1);
        getWritableDatabase().update(TABLE, values, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /** @return 마지막으로 새 항목을 받은 시각. 없으면 0 */
    public long getLastReceivedAt() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT MAX(" + COL_RECEIVED_AT + ") FROM " + TABLE, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }
}
