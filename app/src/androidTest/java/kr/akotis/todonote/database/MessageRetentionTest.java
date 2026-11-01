package kr.akotis.todonote.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.UUID;

import kr.akotis.todonote.model.MessageItem;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.NoteItem;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MessageRetentionTest {
    private Context context;
    private String databaseName;
    private DatabaseHelper helper;
    private File snapshot;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "message_retention_test_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, databaseName);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(databaseName);
        if (snapshot != null) {
            SQLiteDatabase.deleteDatabase(snapshot);
        }
    }

    private long insertMessage(long receivedAt) {
        MessageItem message = new MessageItem("title", "body");
        message.setReceivedAt(receivedAt);
        return helper.insertMessage(message);
    }

    private int count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    @Test
    public void receivingManyMessagesKeepsLatestThirtyAndUnreadCount() {
        long lastId = 0;
        for (int i = 0; i < 100; i++) {
            lastId = insertMessage(i);
        }
        List<MessageItem> messages = helper.getAllMessages();
        assertEquals(30, messages.size());
        assertEquals(lastId, messages.get(0).getId());
        assertEquals(70, messages.get(29).getReceivedAt());
        assertEquals(30, helper.getUnreadMessageCount());
        helper.markMessageAsRead(lastId);
        assertEquals(29, helper.getUnreadMessageCount());
        helper.markAllMessagesAsRead();
        assertEquals(0, helper.getUnreadMessageCount());
        assertEquals(30, count(helper.getReadableDatabase(), "message"));
    }

    @Test
    public void sameTimestampUsesIdAndOlderArrivalsDoNotEvictNewerMessages() {
        long lastId = 0;
        for (int i = 0; i < 35; i++) {
            lastId = insertMessage(100);
        }
        insertMessage(1);
        List<MessageItem> messages = helper.getAllMessages();
        assertEquals(30, messages.size());
        assertEquals(lastId, messages.get(0).getId());
        assertEquals(lastId - 29, messages.get(29).getId());
    }

    @Test
    public void trashIsIncludedInLimitAndRetainedMessagesCanStillBeRestored() {
        long oldestId = insertMessage(1);
        helper.deleteMessage(oldestId);
        assertEquals(1, helper.getDeletedMessages().size());
        helper.restoreMessage(oldestId);
        assertEquals(1, helper.getAllMessages().size());
        helper.deleteMessage(oldestId);
        for (int i = 2; i <= 31; i++) {
            long id = insertMessage(i);
            helper.deleteMessage(id);
        }
        assertEquals(0, helper.getAllMessages().size());
        assertEquals(30, helper.getDeletedMessages().size());
        assertEquals(0, helper.restoreMessage(oldestId));
    }

    @Test
    public void openingLegacyDatabaseTrimsAndReclaimsSpaceWithoutChangingOtherData() {
        helper.insertMemo(new MemoItem("keep memo"));
        helper.insertNote(new NoteItem("keep title", "keep content"));
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", "legacy");
        values.put("body", new String(new char[8192]).replace('\0', 'x'));
        for (int i = 0; i < 100; i++) {
            values.put("received_at", i);
            db.insertOrThrow("message", null, values);
        }
        helper.close();
        long sizeBefore = context.getDatabasePath(databaseName).length();
        assertEquals(30, helper.getAllMessages().size());
        helper.close();
        assertTrue(context.getDatabasePath(databaseName).length() < sizeBefore);
        assertEquals("keep memo", helper.getAllMemos().get(0).getContent());
        assertEquals("keep content", helper.getAllNotes().get(0).getContent());
    }

    @Test
    public void compactBackupIncludesWalDataAndPreservesPasswordAndOtherTables() throws Exception {
        helper.insertMemo(new MemoItem("memo"));
        helper.insertNote(new NoteItem("title", "note"));
        helper.setNotePassword("test-password", "hint");
        SQLiteDatabase db = helper.getWritableDatabase();
        assertTrue(db.enableWriteAheadLogging());
        db.execSQL("INSERT INTO todo (title, detail) VALUES ('todo', 'detail')");
        // 직접 삽입해 WAL에 아직 체크포인트되지 않은 데이터도 검증한다.
        db.execSQL("INSERT INTO message (title, body, received_at) VALUES ('wal', 'body', 100)");
        snapshot = helper.createCompactBackup(context.getCacheDir());
        try (SQLiteDatabase copy = SQLiteDatabase.openDatabase(
                snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            assertEquals(1, count(copy, "message"));
            assertEquals(1, count(copy, "memo"));
            assertEquals(1, count(copy, "note"));
            assertEquals(1, count(copy, "todo"));
            try (Cursor cursor = copy.rawQuery("PRAGMA integrity_check", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("ok", cursor.getString(0));
            }
        }
        assertTrue(DatabaseHelper.verifyPasswordForFile(snapshot.getAbsolutePath(), "test-password"));
        assertEquals("hint", DatabaseHelper.getPasswordHintFromFile(snapshot.getAbsolutePath()));
    }

    @Test
    public void preparingOldBackupKeepsThirtyMessagesAndUnrelatedRows() throws Exception {
        helper.insertNote(new NoteItem("title", "note"));
        snapshot = helper.createCompactBackup(context.getCacheDir());
        try (SQLiteDatabase copy = SQLiteDatabase.openDatabase(
                snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            for (int i = 0; i < 100; i++) {
                copy.execSQL("INSERT INTO message (title, body, received_at) VALUES ('old', 'body', ?)",
                        new Object[]{i});
            }
        }
        DatabaseHelper.prepareImportedDatabase(snapshot);
        try (SQLiteDatabase copy = SQLiteDatabase.openDatabase(
                snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            assertEquals(30, count(copy, "message"));
            assertEquals(1, count(copy, "note"));
        }
        assertEquals(0, helper.getAllMessages().size());
    }
}
