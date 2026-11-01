package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.MessageItem;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.model.ToDoItem;

/** 실제 사용 중인 v12 DB가 데이터 손실 없이 현재 버전으로 올라가는지 확인한다. */
@RunWith(AndroidJUnit4.class)
public class DatabaseUpgradeTest {
    private Context context;
    private String databaseName;
    private String freshName;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "upgrade_test_" + UUID.randomUUID() + ".db";
        freshName = "upgrade_fresh_" + UUID.randomUUID() + ".db";
    }

    @After
    public void tearDown() {
        context.deleteDatabase(databaseName);
        context.deleteDatabase(freshName);
    }

    /** 1.9까지 쓰던 v12 스키마와 데이터를 그대로 만든다. */
    private void createVersion12Database() {
        context.getDatabasePath(databaseName).getParentFile().mkdirs();
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)) {
            db.execSQL("CREATE TABLE todo (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, detail TEXT, " +
                    "date INTEGER, time INTEGER, completed INTEGER DEFAULT 0, checked INTEGER DEFAULT 0, " +
                    "notification_type TEXT, work_request_id TEXT, created_at INTEGER, updated_at INTEGER)");
            db.execSQL("CREATE TABLE memo (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, " +
                    "created_at INTEGER, updated_at INTEGER, memo_order INTEGER DEFAULT 0, deleted_at INTEGER)");
            db.execSQL("CREATE TABLE note (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT NOT NULL, " +
                    "created_at INTEGER, updated_at INTEGER, deleted_at INTEGER, locked INTEGER DEFAULT 0, " +
                    "note_order INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE password_settings (id INTEGER PRIMARY KEY AUTOINCREMENT, password_hash TEXT, " +
                    "password_hint TEXT)");
            db.execSQL("CREATE TABLE message (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, " +
                    "received_at INTEGER, is_read INTEGER DEFAULT 0, deleted_at INTEGER)");

            db.execSQL("INSERT INTO memo (id, content, created_at, updated_at, memo_order) VALUES " +
                    "(1, '둘째 메모', 100, 110, 1), (2, '첫째 메모', 200, 210, 0)");
            db.execSQL("INSERT INTO memo (id, content, created_at, updated_at, memo_order, deleted_at) VALUES " +
                    "(3, '삭제한 메모', 300, 310, 2, 999)");
            db.execSQL("INSERT INTO note (id, title, content, created_at, updated_at, locked, note_order) VALUES " +
                    "(1, '잠긴 노트', '비밀 내용', 100, 110, 1, 1), (2, '일반 노트', '일반 내용', 200, 210, 0, 0)");
            db.execSQL("INSERT INTO note (id, title, content, created_at, updated_at, note_order, deleted_at) VALUES " +
                    "(3, '삭제한 노트', '내용', 300, 310, 2, 888)");
            db.execSQL("INSERT INTO todo (id, title, detail, date, time, notification_type, work_request_id, " +
                    "created_at, updated_at) VALUES (1, '할 일', '설명', 1000, 2000, '10min', 'work-id', 1, 2)");
            db.execSQL("INSERT INTO message (id, title, body, received_at, is_read) VALUES (1, '알림', '본문', 500, 1)");
            db.execSQL("INSERT INTO password_settings (password_hash, password_hint) VALUES ('hash', '힌트')");
            db.setVersion(12);
        }
    }

    @Test
    public void upgradingFromVersion12KeepsAllExistingData() {
        createVersion12Database();

        DatabaseHelper helper = new DatabaseHelper(context, databaseName);
        try {
            List<MemoItem> memos = helper.getAllMemos();
            assertEquals(2, memos.size());
            assertEquals("첫째 메모", memos.get(0).getContent());
            assertEquals("둘째 메모", memos.get(1).getContent());
            assertEquals(210, memos.get(0).getUpdatedAt());
            assertEquals("삭제한 메모", helper.getDeletedMemos().get(0).getContent());
            assertEquals(999, helper.getDeletedMemos().get(0).getDeletedAt());

            List<NoteItem> notes = helper.getAllNotes();
            assertEquals(2, notes.size());
            assertEquals("일반 노트", notes.get(0).getTitle());
            assertEquals("잠긴 노트", notes.get(1).getTitle());
            assertTrue(notes.get(1).isLocked());
            assertEquals("비밀 내용", notes.get(1).getContent());
            assertEquals("삭제한 노트", helper.getDeletedNotes().get(0).getTitle());

            ToDoItem todo = helper.getToDoById(1);
            assertEquals("할 일", todo.getTitle());
            assertEquals(1000, todo.getDate());
            assertEquals("10min", todo.getNotificationType());
            assertEquals("work-id", todo.getWorkRequestId());

            List<MessageItem> messages = helper.getAllMessages();
            assertEquals(1, messages.size());
            assertEquals("본문", messages.get(0).getBody());

            assertEquals("hash", helper.getNotePasswordHash());
            assertEquals("힌트", helper.getNotePasswordHint());
        } finally {
            helper.close();
        }
    }

    @Test
    public void firstRunWithManyMessagesTrimsOnlyMessages() {
        createVersion12Database();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).getPath(),
                null, SQLiteDatabase.OPEN_READWRITE)) {
            for (int i = 0; i < 50; i++) {
                db.execSQL("INSERT INTO message (title, body, received_at) VALUES ('알림', '본문', ?)",
                        new Object[]{1000 + i});
            }
        }

        // 한 번 여는 동안 업그레이드, 알림 정리, 공간 회수가 이어서 실행된다.
        DatabaseHelper helper = new DatabaseHelper(context, databaseName);
        try {
            assertEquals(DatabaseHelper.MAX_STORED_MESSAGES, helper.getAllMessages().size());
            assertEquals(1049, helper.getAllMessages().get(0).getReceivedAt());
            assertEquals(2, helper.getAllMemos().size());
            assertEquals(2, helper.getAllNotes().size());
            assertEquals("삭제한 메모", helper.getDeletedMemos().get(0).getContent());
            assertEquals("삭제한 노트", helper.getDeletedNotes().get(0).getTitle());
            assertEquals("hash", helper.getNotePasswordHash());
        } finally {
            helper.close();
        }
    }

    @Test
    public void newFeaturesWorkOnAnUpgradedDatabase() {
        createVersion12Database();

        DatabaseHelper helper = new DatabaseHelper(context, databaseName);
        try {
            NoteItem note = helper.getNoteById(2);
            note.setContent("수정한 내용");
            assertEquals(1, helper.updateNote(note));
            assertEquals("일반 내용", helper.getNoteHistory(2).get(0).getContent());
            assertEquals(210, helper.getNoteHistory(2).get(0).getSavedAt());

            helper.saveNoteDraft(1, "잠긴 노트", "임시");
            assertEquals("임시", helper.getNoteDraft(1).getContent());
            helper.deleteNoteDraft(1);
            assertNull(helper.getNoteDraft(1));
        } finally {
            helper.close();
        }
    }

    @Test
    public void upgradedSchemaMatchesFreshInstall() {
        createVersion12Database();
        DatabaseHelper upgraded = new DatabaseHelper(context, databaseName);
        DatabaseHelper fresh = new DatabaseHelper(context, freshName);
        try {
            assertEquals(describeSchema(fresh.getReadableDatabase()), describeSchema(upgraded.getReadableDatabase()));
        } finally {
            upgraded.close();
            fresh.close();
        }
    }

    /** 테이블별 컬럼 이름, 형식, 기본값과 인덱스 목록. 컬럼 순서는 비교하지 않는다. */
    private static Map<String, String> describeSchema(SQLiteDatabase db) {
        Map<String, String> schema = new TreeMap<>();
        try (Cursor objects = db.rawQuery("SELECT type, name FROM sqlite_master " +
                "WHERE name NOT LIKE 'sqlite_%' AND name != 'android_metadata'", null)) {
            while (objects.moveToNext()) {
                String type = objects.getString(0);
                String name = objects.getString(1);
                if (!"table".equals(type)) {
                    schema.put(type + ":" + name, "");
                    continue;
                }
                Map<String, String> columns = new TreeMap<>();
                try (Cursor info = db.rawQuery("PRAGMA table_info(" + name + ")", null)) {
                    while (info.moveToNext()) {
                        columns.put(info.getString(1), info.getString(2) + "|" + info.getInt(3) + "|" + info.getString(4));
                    }
                }
                schema.put("table:" + name, columns.toString());
            }
        }
        return schema;
    }
}
