package kr.akotis.todonote.database;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.MessageItem;
import kr.akotis.todonote.model.NoteItem;

@RunWith(AndroidJUnit4.class)
public class ImportSafetyTest {
    private Context context;
    private String databaseName;
    private DatabaseHelper helper;
    private final List<File> createdFiles = new ArrayList<>();

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "import_test_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, databaseName);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(databaseName);
        for (File file : createdFiles) {
            SQLiteDatabase.deleteDatabase(file);
            file.delete();
        }
        for (File copy : DamagedDatabaseKeeper.pendingCopies(context)) {
            copy.delete();
        }
        DamagedDatabaseKeeper.markNotified(context);
    }

    private File tempFile(byte[] content) throws IOException {
        File file = File.createTempFile("import_check_", ".db", context.getCacheDir());
        createdFiles.add(file);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
        return file;
    }

    private File snapshot() throws IOException {
        File file = helper.createCompactBackup(context.getCacheDir());
        createdFiles.add(file);
        return file;
    }

    @Test
    public void validBackupPasses() throws IOException {
        helper.insertMemo(new MemoItem("메모"));
        assertEquals(DatabaseHelper.ImportCheck.OK, DatabaseHelper.checkImportFile(snapshot()));
    }

    @Test
    public void emptyAndForeignFilesAreRejected() throws IOException {
        assertEquals(DatabaseHelper.ImportCheck.NOT_BACKUP, DatabaseHelper.checkImportFile(tempFile(new byte[0])));
        assertEquals(DatabaseHelper.ImportCheck.NOT_BACKUP,
                DatabaseHelper.checkImportFile(tempFile("이 파일은 메모장 문서입니다. 백업이 아닙니다.".getBytes(StandardCharsets.UTF_8))));
        assertEquals(DatabaseHelper.ImportCheck.NOT_BACKUP,
                DatabaseHelper.checkImportFile(new File(context.getCacheDir(), "does_not_exist.db")));
    }

    @Test
    public void sqliteFileFromAnotherAppIsRejected() throws IOException {
        File other = tempFile(new byte[0]);
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(other, null)) {
            db.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY)");
            db.setVersion(3);
        }
        assertEquals(DatabaseHelper.ImportCheck.NOT_BACKUP, DatabaseHelper.checkImportFile(other));
    }

    @Test
    public void backupFromNewerAppVersionIsRejected() throws IOException {
        File newer = snapshot();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(newer.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            db.setVersion(db.getVersion() + 1);
        }
        assertEquals(DatabaseHelper.ImportCheck.TOO_NEW, DatabaseHelper.checkImportFile(newer));
    }

    @Test
    public void damagedBackupIsRejectedAndNotDeleted() throws IOException {
        for (int i = 0; i < 200; i++) {
            helper.insertMemo(new MemoItem("메모 내용 " + i));
        }
        File damaged = snapshot();
        // 헤더는 그대로 두고 데이터 페이지를 망가뜨린다.
        try (RandomAccessFile raf = new RandomAccessFile(damaged, "rw")) {
            byte[] garbage = new byte[(int) raf.length() - 4096];
            java.util.Arrays.fill(garbage, (byte) 0x5A);
            raf.seek(4096);
            raf.write(garbage);
        }
        assertEquals(DatabaseHelper.ImportCheck.DAMAGED, DatabaseHelper.checkImportFile(damaged));
        assertTrue("검사만으로 파일이 지워지면 안 된다", damaged.exists());
    }

    @Test
    public void fileWithSameTableNamesButWrongColumnsCannotBeOpened() throws IOException {
        File lookalike = tempFile(new byte[0]);
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(lookalike, null)) {
            db.execSQL("CREATE TABLE memo (id INTEGER PRIMARY KEY)");
            db.execSQL("CREATE TABLE note (id INTEGER PRIMARY KEY)");
            db.setVersion(14);
        }
        // 형식 검사는 통과하지만 교체하면 앱이 시작할 때마다 종료되는 파일이다.
        assertEquals(DatabaseHelper.ImportCheck.OK, DatabaseHelper.checkImportFile(lookalike));
        assertFalse(DatabaseHelper.canOpenImportedFile(context, lookalike));
        assertTrue("검사가 손상 사본을 남기면 안 된다", DamagedDatabaseKeeper.pendingCopies(context).isEmpty());
    }

    @Test
    public void realBackupCanBeOpenedAndTrialLeavesItUnchanged() throws IOException {
        helper.insertMemo(new MemoItem("메모"));
        File backup = snapshot();
        byte[] before = Files.readAllBytes(backup.toPath());

        assertTrue(DatabaseHelper.canOpenImportedFile(context, backup));

        assertArrayEquals(before, Files.readAllBytes(backup.toPath()));
    }

    @Test
    public void version12BackupPassesImportAndKeepsData() throws IOException {
        // 1.8까지의 내보내기: v12 스키마의 DB 파일 그대로
        File backup = tempFile(new byte[0]);
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(backup, null)) {
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
            db.execSQL("INSERT INTO memo (content, created_at, updated_at) VALUES ('옛 메모', 1, 2)");
            db.execSQL("INSERT INTO note (title, content, created_at, updated_at) VALUES ('옛 노트', '옛 내용', 1, 2)");
            db.setVersion(12);
        }

        assertEquals(DatabaseHelper.ImportCheck.OK, DatabaseHelper.checkImportFile(backup));
        assertTrue(DatabaseHelper.canOpenImportedFile(context, backup));
        DatabaseHelper.prepareImportedDatabase(backup);

        // 교체한 뒤 앱이 여는 것과 같은 경로로 연다.
        DatabaseHelper restored = new DatabaseHelper(context, backup.getAbsolutePath());
        try {
            assertEquals("옛 메모", restored.getAllMemos().get(0).getContent());
            assertEquals("옛 내용", restored.getAllNotes().get(0).getContent());
            assertTrue(restored.getNoteHistory(1).isEmpty());
        } finally {
            restored.close();
        }
    }

    @Test
    public void historyAndDraftsSurviveBackupRoundTrip() throws IOException {
        long noteId = helper.insertNote(new NoteItem("제목", "첫 내용"));
        NoteItem note = helper.getNoteById(noteId);
        note.setContent("둘째 내용");
        helper.updateNote(note);
        helper.saveNoteDraft(noteId, "제목", "임시 내용");

        File backup = snapshot();

        DatabaseHelper restored = new DatabaseHelper(context, backup.getAbsolutePath());
        try {
            assertEquals("첫 내용", restored.getNoteHistory(noteId).get(0).getContent());
            assertEquals("임시 내용", restored.getNoteDraft(noteId).getContent());
        } finally {
            restored.close();
        }
    }

    @Test
    public void savingFromAnOpenEditorDoesNotRestoreAnOldLock() {
        helper.setNotePassword("암호", null);
        long id = helper.insertNote(new NoteItem("제목", "내용"));
        helper.toggleNoteLock(id, true);
        // 잠긴 노트를 연 편집 화면이 이 값을 들고 있는 동안 암호가 제거되는 경우
        NoteItem openInEditor = helper.getNoteById(id);
        helper.removeNotePassword();

        openInEditor.setContent("수정");
        helper.updateNote(openInEditor);

        assertFalse(helper.getNoteById(id).isLocked());
        assertEquals("수정", helper.getNoteById(id).getContent());
    }

    @Test
    public void snapshotDoesNotLeaveConnectionOpen() throws IOException {
        helper.insertMemo(new MemoItem("메모"));
        snapshot();
        // 연결이 닫혀 있으면 WAL 파일도 남지 않아 DB 파일을 안전하게 교체할 수 있다.
        File dbFile = context.getDatabasePath(databaseName);
        assertFalse(new File(dbFile.getPath() + "-wal").exists());
    }

    @Test
    public void damagedDatabaseIsKeptBeforeAndroidDeletesIt() throws IOException {
        helper.insertMemo(new MemoItem("살려야 할 메모"));
        helper.close();
        File dbFile = context.getDatabasePath(databaseName);
        byte[] garbage = new byte[8192];
        java.util.Arrays.fill(garbage, (byte) 0x41);
        Files.write(dbFile.toPath(), garbage);

        // 열기에 실패하면 Android 기본 처리가 파일을 지우고 빈 DB를 새로 만든다.
        assertTrue(helper.getAllMemos().isEmpty());

        List<File> copies = DamagedDatabaseKeeper.pendingCopies(context);
        assertEquals(1, copies.size());
        assertArrayEquals(garbage, Files.readAllBytes(copies.get(0).toPath()));

        DamagedDatabaseKeeper.markNotified(context);
        assertTrue(DamagedDatabaseKeeper.pendingCopies(context).isEmpty());
        copies.get(0).delete();
    }

    @Test
    public void emptyingTrashAlsoRemovesTrashedMessagesAndKeepsLiveOnes() {
        long liveMemo = helper.insertMemo(new MemoItem("유지"));
        long trashedMemo = helper.insertMemo(new MemoItem("삭제"));
        long trashedNote = helper.insertNote(new NoteItem("삭제", "내용"));
        long liveMessage = helper.insertMessage(new MessageItem("유지", "본문"));
        long trashedMessage = helper.insertMessage(new MessageItem("삭제", "본문"));
        helper.deleteMemo(trashedMemo);
        helper.deleteNote(trashedNote);
        helper.deleteMessage(trashedMessage);

        assertEquals(3, helper.emptyTrash());

        assertEquals(liveMemo, helper.getAllMemos().get(0).getId());
        assertEquals(liveMessage, helper.getAllMessages().get(0).getId());
        assertTrue(helper.getDeletedMemos().isEmpty());
        assertTrue(helper.getDeletedNotes().isEmpty());
        assertTrue(helper.getDeletedMessages().isEmpty());
    }

    @Test
    public void currentAndLegacyPasswordHashesBothVerify() throws Exception {
        helper.setNotePassword("현재암호", "힌트");
        assertTrue(helper.verifyNotePassword("현재암호"));
        assertFalse(helper.verifyNotePassword("틀린암호"));

        // 1.5까지 쓰던 형식: 암호의 SHA-256을 Base64로 저장
        String legacyHash = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest("옛암호".getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("password_hash", legacyHash);
        db.update("password_settings", values, null, null);

        assertTrue(helper.verifyNotePassword("옛암호"));
        assertFalse(helper.verifyNotePassword("현재암호"));
        assertTrue(DatabaseHelper.verifyPasswordForFile(context.getDatabasePath(databaseName).getPath(), "옛암호"));
        assertFalse(DatabaseHelper.verifyPasswordForFile(context.getDatabasePath(databaseName).getPath(), "틀린암호"));
    }

    @Test
    public void removingPasswordUnlocksNotesInTrashToo() {
        helper.setNotePassword("암호", null);
        long live = helper.insertNote(new NoteItem("일반", "내용"));
        long trashed = helper.insertNote(new NoteItem("휴지통", "내용"));
        helper.toggleNoteLock(live, true);
        helper.toggleNoteLock(trashed, true);
        helper.deleteNote(trashed);
        long liveUpdatedAt = helper.getNoteById(live).getUpdatedAt();

        helper.removeNotePassword();

        assertFalse(helper.hasNotePassword());
        assertFalse(helper.getNoteById(live).isLocked());
        assertFalse(helper.getNoteById(trashed).isLocked());
        assertEquals(liveUpdatedAt, helper.getNoteById(live).getUpdatedAt());
    }

    @Test
    public void passwordSurvivesSnapshotRoundTrip() throws IOException {
        helper.setNotePassword("암호", "힌트");
        File backup = snapshot();
        assertTrue(DatabaseHelper.verifyPasswordForFile(backup.getPath(), "암호"));
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(backup.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("ok", cursor.getString(0));
        }
    }
}
