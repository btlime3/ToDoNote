package kr.akotis.todonote.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import kr.akotis.todonote.model.DraftItem;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.model.NoteVersion;
import kr.akotis.todonote.model.ToDoItem;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ToDoNote.db";
    private static final int DATABASE_VERSION = 14;
    public static final int MAX_STORED_MESSAGES = 30;
    /** 노트 하나당 보관하는 이전 버전 개수. */
    public static final int MAX_NOTE_VERSIONS = 30;

    /** SQLite 파일의 첫 16바이트. */
    private static final byte[] SQLITE_FILE_HEADER =
            "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    /** 임시 저장본의 대상이 아직 저장되지 않은 새 항목임을 나타낸다. 실제 ID는 1부터 시작한다. */
    public static final long DRAFT_NEW_ITEM = 0;
    
    // 암호 설정 테이블
    private static final String TABLE_PASSWORD = "password_settings";
    private static final String COL_PASSWORD_ID = "id";
    private static final String COL_PASSWORD_HASH = "password_hash";
    private static final String COL_PASSWORD_HINT = "password_hint";

    private Context context;

    // ToDo 테이블
    private static final String TABLE_TODO = "todo";
    private static final String COL_TODO_ID = "id";
    private static final String COL_TODO_TITLE = "title";
    private static final String COL_TODO_DETAIL = "detail";
    private static final String COL_TODO_DATE = "date";
    private static final String COL_TODO_TIME = "time";
    private static final String COL_TODO_COMPLETED = "completed";
    private static final String COL_TODO_CHECKED = "checked";
    private static final String COL_TODO_NOTIFICATION_TYPE = "notification_type";
    private static final String COL_TODO_WORK_REQUEST_ID = "work_request_id";
    private static final String COL_TODO_CREATED_AT = "created_at";
    private static final String COL_TODO_UPDATED_AT = "updated_at";

    // Memo 테이블
    private static final String TABLE_MEMO = "memo";
    private static final String COL_MEMO_ID = "id";
    private static final String COL_MEMO_CONTENT = "content";
    private static final String COL_MEMO_CREATED_AT = "created_at";
    private static final String COL_MEMO_UPDATED_AT = "updated_at";
    private static final String COL_MEMO_ORDER = "memo_order";
    private static final String COL_MEMO_DELETED_AT = "deleted_at";

    // Note 테이블
    private static final String TABLE_NOTE = "note";
    private static final String COL_NOTE_ID = "id";
    private static final String COL_NOTE_TITLE = "title";
    private static final String COL_NOTE_CONTENT = "content";
    private static final String COL_NOTE_CREATED_AT = "created_at";
    private static final String COL_NOTE_UPDATED_AT = "updated_at";
    private static final String COL_NOTE_DELETED_AT = "deleted_at";
    private static final String COL_NOTE_LOCKED = "locked";
    private static final String COL_NOTE_ORDER = "note_order";

    // Message 테이블 (FCM 알림)
    private static final String TABLE_MESSAGE = "message";
    private static final String COL_MESSAGE_ID = "id";
    private static final String COL_MESSAGE_TITLE = "title";
    private static final String COL_MESSAGE_BODY = "body";
    private static final String COL_MESSAGE_RECEIVED_AT = "received_at";
    private static final String COL_MESSAGE_IS_READ = "is_read";
    private static final String COL_MESSAGE_DELETED_AT = "deleted_at";

    // Draft 테이블 (저장하지 않은 편집 내용)
    private static final String TABLE_DRAFT = "draft";
    private static final String COL_DRAFT_TYPE = "draft_type";
    private static final String COL_DRAFT_TARGET_ID = "target_id";
    private static final String COL_DRAFT_TITLE = "title";
    private static final String COL_DRAFT_CONTENT = "content";
    private static final String COL_DRAFT_SAVED_AT = "saved_at";
    private static final String DRAFT_TYPE_NOTE = "note";
    private static final String DRAFT_TYPE_MEMO = "memo";
    private static final String CREATE_DRAFT_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_DRAFT + " (" +
            COL_DRAFT_TYPE + " TEXT NOT NULL, " +
            COL_DRAFT_TARGET_ID + " INTEGER NOT NULL, " +
            COL_DRAFT_TITLE + " TEXT, " +
            COL_DRAFT_CONTENT + " TEXT, " +
            COL_DRAFT_SAVED_AT + " INTEGER, " +
            "PRIMARY KEY (" + COL_DRAFT_TYPE + ", " + COL_DRAFT_TARGET_ID + "))";

    // Note History 테이블 (노트 수정 전 내용)
    private static final String TABLE_NOTE_HISTORY = "note_history";
    private static final String COL_HISTORY_ID = "id";
    private static final String COL_HISTORY_NOTE_ID = "note_id";
    private static final String COL_HISTORY_TITLE = "title";
    private static final String COL_HISTORY_CONTENT = "content";
    private static final String COL_HISTORY_SAVED_AT = "saved_at";
    private static final String CREATE_NOTE_HISTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NOTE_HISTORY + " (" +
            COL_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_HISTORY_NOTE_ID + " INTEGER NOT NULL, " +
            COL_HISTORY_TITLE + " TEXT, " +
            COL_HISTORY_CONTENT + " TEXT, " +
            COL_HISTORY_SAVED_AT + " INTEGER)";
    private static final String CREATE_NOTE_HISTORY_INDEX = "CREATE INDEX IF NOT EXISTS idx_note_history_note_id ON " +
            TABLE_NOTE_HISTORY + " (" + COL_HISTORY_NOTE_ID + ")";

    public DatabaseHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    DatabaseHelper(Context context, String databaseName) {
        this(context, databaseName, new DamagedDatabaseKeeper(context));
    }

    private DatabaseHelper(Context context, String databaseName, DatabaseErrorHandler errorHandler) {
        super(context, databaseName, null, DATABASE_VERSION, errorHandler);
        this.context = context;
    }

    /**
     * 가져오려는 파일의 사본을 실제로 열어 앱이 쓰는 테이블과 컬럼을 모두 읽을 수 있는지 확인한다.
     * 이름만 같은 테이블이 있는 파일로 교체하면 앱이 시작할 때마다 종료되어 되돌릴 수 없게 된다.
     * 옛 버전 백업은 이 사본에서 업그레이드까지 확인되며, 원본 파일은 바뀌지 않는다.
     */
    public static boolean canOpenImportedFile(Context context, File file) {
        File trial = new File(context.getCacheDir(), "temp_import_db_trial_" + System.nanoTime() + ".db");
        DatabaseHelper helper = null;
        try {
            Files.copy(file.toPath(), trial.toPath());
            helper = new DatabaseHelper(context, trial.getAbsolutePath(), corruptDb -> { });
            helper.getAllMemos();
            helper.getDeletedMemos();
            helper.getAllNotes();
            helper.getDeletedNotes();
            helper.getAllToDos();
            helper.getAllMessages();
            helper.getDeletedMessages();
            helper.getNotePasswordHash();
            helper.getNotePasswordHint();
            helper.getNoteHistory(0);
            helper.getNoteDraft(DRAFT_NEW_ITEM);
            helper.getMemoDraft();
            return true;
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("DatabaseHelper", "Imported file cannot be opened by this app", e);
            return false;
        } finally {
            if (helper != null) {
                helper.close();
            }
            SQLiteDatabase.deleteDatabase(trial);
        }
    }

    /** 가져오려는 파일을 검사한 결과. */
    public enum ImportCheck {
        OK, NOT_BACKUP, DAMAGED, TOO_NEW
    }

    /**
     * 현재 데이터를 교체하기 전에 이 앱의 온전한 백업인지 확인한다.
     * 검사 중 손상이 발견되어도 파일이 삭제되지 않도록 아무 일도 하지 않는 오류 처리기를 쓴다.
     */
    public static ImportCheck checkImportFile(File file) {
        byte[] header = new byte[SQLITE_FILE_HEADER.length];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(header) != header.length || !java.util.Arrays.equals(header, SQLITE_FILE_HEADER)) {
                return ImportCheck.NOT_BACKUP;
            }
        } catch (IOException e) {
            return ImportCheck.NOT_BACKUP;
        }

        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY, corruptDb -> { })) {
            if (db.getVersion() > DATABASE_VERSION) {
                return ImportCheck.TOO_NEW;
            }
            if (db.getVersion() < 1 || !hasTable(db, TABLE_MEMO) || !hasTable(db, TABLE_NOTE)) {
                return ImportCheck.NOT_BACKUP;
            }
            try (Cursor cursor = db.rawQuery("PRAGMA quick_check(1)", null)) {
                return cursor.moveToFirst() && "ok".equalsIgnoreCase(cursor.getString(0))
                        ? ImportCheck.OK : ImportCheck.DAMAGED;
            }
        } catch (SQLiteException e) {
            return ImportCheck.DAMAGED;
        }
    }

    private static boolean hasTable(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new String[]{table})) {
            return cursor.moveToFirst();
        }
    }

    /**
     * 데이터베이스 파일의 절대 경로를 반환합니다 (Context를 통해).
     */
    public static String getDatabasePath(Context context) {
        return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
    }

    /**
     * 외부 DB 파일에서 비밀번호 해시를 읽어옵니다.
     * @param dbFilePath 읽을 DB 파일 경로
     * @return 비밀번호 해시 (없으면 null)
     */
    public static String getPasswordHashFromFile(String dbFilePath) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFilePath, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.query(TABLE_PASSWORD, new String[]{COL_PASSWORD_HASH}, null, null, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HASH));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return null;
    }

    /**
     * 외부 DB 파일에서 비밀번호 힌트를 읽어옵니다.
     * @param dbFilePath 읽을 DB 파일 경로
     * @return 비밀번호 힌트 (없으면 null)
     */
    public static String getPasswordHintFromFile(String dbFilePath) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFilePath, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.query(TABLE_PASSWORD, new String[]{COL_PASSWORD_HINT}, null, null, null, null, null);
            if (cursor.moveToFirst()) {
                int hintIndex = cursor.getColumnIndex(COL_PASSWORD_HINT);
                if (hintIndex >= 0 && !cursor.isNull(hintIndex)) {
                    return cursor.getString(hintIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return null;
    }

    /**
     * 외부 DB 파일의 비밀번호를 검증합니다.
     * @param dbFilePath DB 파일 경로
     * @param password 검증할 비밀번호
     * @return 비밀번호가 맞으면 true
     */
    public static boolean verifyPasswordForFile(String dbFilePath, String password) {
        String storedHash = getPasswordHashFromFile(dbFilePath);
        if (storedHash == null) {
            return false;
        }

        return matchesStoredHash(storedHash, password);
    }

    /** 현재 형식(PBKDF2)과 1.5까지 쓰던 형식(SHA-256) 모두 확인한다. */
    private static boolean matchesStoredHash(String storedHash, String password) {
        try {
            byte[] stored = Base64.decode(storedHash, Base64.NO_WRAP);
            if (stored.length == 48) {
                // Salt 16 + PBKDF2 32
                byte[] salt = java.util.Arrays.copyOfRange(stored, 0, 16);
                byte[] expected = java.util.Arrays.copyOfRange(stored, 16, 48);
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
                return MessageDigest.isEqual(expected, factory.generateSecret(spec).getEncoded());
            }
            if (stored.length == 32) {
                byte[] input = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
                return MessageDigest.isEqual(stored, input);
            }
            return false;
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Could not verify password", e);
            return false;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // ToDo 테이블 생성
        String createTodoTable = "CREATE TABLE " + TABLE_TODO + " (" +
                COL_TODO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TODO_TITLE + " TEXT NOT NULL, " +
                COL_TODO_DETAIL + " TEXT, " +
                COL_TODO_DATE + " INTEGER, " +
                COL_TODO_TIME + " INTEGER, " +
                COL_TODO_COMPLETED + " INTEGER DEFAULT 0, " +
                COL_TODO_CHECKED + " INTEGER DEFAULT 0, " +
                COL_TODO_NOTIFICATION_TYPE + " TEXT, " +
                COL_TODO_WORK_REQUEST_ID + " TEXT, " +
                COL_TODO_CREATED_AT + " INTEGER, " +
                COL_TODO_UPDATED_AT + " INTEGER)";
        db.execSQL(createTodoTable);

        // Memo 테이블 생성
        String createMemoTable = "CREATE TABLE " + TABLE_MEMO + " (" +
                COL_MEMO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MEMO_CONTENT + " TEXT NOT NULL, " +
                COL_MEMO_CREATED_AT + " INTEGER, " +
                COL_MEMO_UPDATED_AT + " INTEGER, " +
                COL_MEMO_ORDER + " INTEGER DEFAULT 0, " +
                COL_MEMO_DELETED_AT + " INTEGER)";
        db.execSQL(createMemoTable);

        // Note 테이블 생성
        String createNoteTable = "CREATE TABLE " + TABLE_NOTE + " (" +
                COL_NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NOTE_TITLE + " TEXT, " +
                COL_NOTE_CONTENT + " TEXT NOT NULL, " +
                COL_NOTE_CREATED_AT + " INTEGER, " +
                COL_NOTE_UPDATED_AT + " INTEGER, " +
                COL_NOTE_DELETED_AT + " INTEGER, " +
                COL_NOTE_LOCKED + " INTEGER DEFAULT 0, " +
                COL_NOTE_ORDER + " INTEGER DEFAULT 0)";
        db.execSQL(createNoteTable);

        // 암호 설정 테이블 생성
            String createPasswordTable = "CREATE TABLE " + TABLE_PASSWORD + " (" +
                    COL_PASSWORD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PASSWORD_HASH + " TEXT, " +
                    COL_PASSWORD_HINT + " TEXT)";
            db.execSQL(createPasswordTable);

        // Message 테이블 생성 (FCM 알림)
        String createMessageTable = "CREATE TABLE " + TABLE_MESSAGE + " (" +
                COL_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MESSAGE_TITLE + " TEXT, " +
                COL_MESSAGE_BODY + " TEXT, " +
                COL_MESSAGE_RECEIVED_AT + " INTEGER, " +
                COL_MESSAGE_IS_READ + " INTEGER DEFAULT 0, " +
                COL_MESSAGE_DELETED_AT + " INTEGER)";
        db.execSQL(createMessageTable);

        db.execSQL(CREATE_DRAFT_TABLE);

        db.execSQL(CREATE_NOTE_HISTORY_TABLE);
        db.execSQL(CREATE_NOTE_HISTORY_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // order 컬럼 추가
            db.execSQL("ALTER TABLE " + TABLE_MEMO + " ADD COLUMN " + COL_MEMO_ORDER + " INTEGER DEFAULT 0");
            // 기존 데이터에 order 값 설정
            db.execSQL("UPDATE " + TABLE_MEMO + " SET " + COL_MEMO_ORDER + " = " + COL_MEMO_ID);
        }
        if (oldVersion < 3) {
            // ToDo 테이블 구조 변경 (content -> title, detail, date, time)
            // 기존 테이블 백업
            db.execSQL("ALTER TABLE " + TABLE_TODO + " RENAME TO " + TABLE_TODO + "_old");
            // 새 테이블 생성
            String createTodoTable = "CREATE TABLE " + TABLE_TODO + " (" +
                    COL_TODO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TODO_TITLE + " TEXT NOT NULL, " +
                    COL_TODO_DETAIL + " TEXT, " +
                    COL_TODO_DATE + " INTEGER, " +
                    COL_TODO_TIME + " INTEGER, " +
                    COL_TODO_COMPLETED + " INTEGER DEFAULT 0, " +
                    COL_TODO_CHECKED + " INTEGER DEFAULT 0, " +
                    COL_TODO_CREATED_AT + " INTEGER, " +
                    COL_TODO_UPDATED_AT + " INTEGER)";
            db.execSQL(createTodoTable);
            // 기존 데이터 마이그레이션 (content를 title로)
            db.execSQL("INSERT INTO " + TABLE_TODO + " (" +
                    COL_TODO_ID + ", " + COL_TODO_TITLE + ", " + COL_TODO_DETAIL + ", " +
                    COL_TODO_DATE + ", " + COL_TODO_TIME + ", " + COL_TODO_COMPLETED + ", " +
                    COL_TODO_CHECKED + ", " + COL_TODO_CREATED_AT + ", " + COL_TODO_UPDATED_AT + ") " +
                    "SELECT " + COL_TODO_ID + ", content, '', " +
                    System.currentTimeMillis() + ", " + System.currentTimeMillis() + ", " +
                    COL_TODO_COMPLETED + ", 0, " + COL_TODO_CREATED_AT + ", " + COL_TODO_UPDATED_AT +
                    " FROM " + TABLE_TODO + "_old");
            // 백업 테이블 삭제
            db.execSQL("DROP TABLE " + TABLE_TODO + "_old");
        }
        if (oldVersion < 4) {
            // checked 컬럼 추가
            db.execSQL("ALTER TABLE " + TABLE_TODO + " ADD COLUMN " + COL_TODO_CHECKED + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 5) {
            // deleted_at 컬럼 추가 (메모, 노트)
            db.execSQL("ALTER TABLE " + TABLE_MEMO + " ADD COLUMN " + COL_MEMO_DELETED_AT + " INTEGER");
            db.execSQL("ALTER TABLE " + TABLE_NOTE + " ADD COLUMN " + COL_NOTE_DELETED_AT + " INTEGER");
        }
        if (oldVersion < 6) {
            // locked 컬럼 추가 (노트)
            db.execSQL("ALTER TABLE " + TABLE_NOTE + " ADD COLUMN " + COL_NOTE_LOCKED + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 7) {
            // 암호 설정 테이블 생성
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PASSWORD + " (" +
                    COL_PASSWORD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PASSWORD_HASH + " TEXT)");
            
            // 기존 SharedPreferences에서 암호 해시를 가져와서 DB에 저장
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences("ToDoNotePrefs", Context.MODE_PRIVATE);
                String existingHash = prefs.getString("note_password_hash", null);
                if (existingHash != null) {
                    ContentValues values = new ContentValues();
                    values.put(COL_PASSWORD_HASH, existingHash);
                    db.insert(TABLE_PASSWORD, null, values);
                    // SharedPreferences에서 제거
                    prefs.edit().remove("note_password_hash").apply();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (oldVersion < 8) {
            // 알림 관련 컬럼 추가
            db.execSQL("ALTER TABLE " + TABLE_TODO + " ADD COLUMN " + COL_TODO_NOTIFICATION_TYPE + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_TODO + " ADD COLUMN " + COL_TODO_WORK_REQUEST_ID + " TEXT");
        }
        if (oldVersion < 9) {
            // Message 테이블 생성 (FCM 알림)
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGE + " (" +
                    COL_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_MESSAGE_TITLE + " TEXT, " +
                    COL_MESSAGE_BODY + " TEXT, " +
                    COL_MESSAGE_RECEIVED_AT + " INTEGER, " +
                    COL_MESSAGE_IS_READ + " INTEGER DEFAULT 0)");
        }
        if (oldVersion < 10) {
            // Message 테이블에 deleted_at 컬럼 추가
            db.execSQL("ALTER TABLE " + TABLE_MESSAGE + " ADD COLUMN " + COL_MESSAGE_DELETED_AT + " INTEGER");
        }
        if (oldVersion < 11) {
            // Note 테이블에 order 컬럼 추가
            db.execSQL("ALTER TABLE " + TABLE_NOTE + " ADD COLUMN " + COL_NOTE_ORDER + " INTEGER DEFAULT 0");
            // 기존 데이터에 order 값 설정
            db.execSQL("UPDATE " + TABLE_NOTE + " SET " + COL_NOTE_ORDER + " = " + COL_NOTE_ID);
        }
        if (oldVersion < 12) {
            // 암호 설정 테이블에 힌트 컬럼 추가
            db.execSQL("ALTER TABLE " + TABLE_PASSWORD + " ADD COLUMN " + COL_PASSWORD_HINT + " TEXT");
        }
        if (oldVersion < 13) {
            // 저장하지 않은 편집 내용을 보관하는 테이블 추가
            db.execSQL(CREATE_DRAFT_TABLE);
        }
        if (oldVersion < 14) {
            // 노트 수정 이력 테이블 추가
            db.execSQL(CREATE_NOTE_HISTORY_TABLE);
            db.execSQL(CREATE_NOTE_HISTORY_INDEX);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly() && trimMessages(db) > 0) {
            // 기존 DB와 오래된 백업의 초과 알림을 정리한 경우에만 공간 회수.
            // 이후 수신은 최대 30개를 유지하며 빈 페이지를 재사용한다.
            try {
                db.execSQL("VACUUM");
            } catch (SQLiteException e) {
                // 공간 회수 실패가 앱 실행이나 알림 수신을 막지 않도록 한다.
                android.util.Log.w("DatabaseHelper", "Could not compact message storage", e);
            }
        }
    }

    private static int trimMessages(SQLiteDatabase db) {
        // 휴지통의 알림도 포함해서 총 30개. 같은 수신 시각이면 ID로 순서를 고정한다.
        return db.delete(TABLE_MESSAGE, COL_MESSAGE_ID + " NOT IN (SELECT " +
                COL_MESSAGE_ID + " FROM " + TABLE_MESSAGE + " ORDER BY " +
                COL_MESSAGE_RECEIVED_AT + " DESC, " + COL_MESSAGE_ID +
                " DESC LIMIT " + MAX_STORED_MESSAGES + ")", null);
    }

    /** WAL의 데이터도 포함한 일관된 사본을 만들고 빈 페이지를 제거한다. */
    public File createCompactBackup(File directory) throws IOException {
        File snapshot = File.createTempFile("todonote_backup_", ".db", directory);
        try {
            SQLiteDatabase db = getWritableDatabase();
            try {
                db.execSQL("VACUUM INTO ?", new Object[]{snapshot.getAbsolutePath()});
            } finally {
                db.close();
            }
            return snapshot;
        } catch (RuntimeException e) {
            snapshot.delete();
            throw e;
        }
    }

    /** 인증이 끝난 가져오기 사본만 정리하며 메모, 노트, 할 일은 변경하지 않는다. */
    public static void prepareImportedDatabase(File file) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            // FCM 테이블이 없었던 예전 백업은 기존 onUpgrade에 맡긴다.
            try (Cursor cursor = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                    new String[]{TABLE_MESSAGE})) {
                if (cursor.moveToFirst()) {
                    trimMessages(db);
                }
            }
            db.execSQL("VACUUM");
        }
    }

    // ========== ToDo CRUD ==========

    public long insertToDo(ToDoItem todo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TODO_TITLE, todo.getTitle());
        values.put(COL_TODO_DETAIL, todo.getDetail());
        values.put(COL_TODO_DATE, todo.getDate());
        values.put(COL_TODO_TIME, todo.getTime());
        values.put(COL_TODO_COMPLETED, todo.isCompleted() ? 1 : 0);
        values.put(COL_TODO_CHECKED, todo.isChecked() ? 1 : 0);
        values.put(COL_TODO_NOTIFICATION_TYPE, todo.getNotificationType());
        values.put(COL_TODO_WORK_REQUEST_ID, todo.getWorkRequestId());
        values.put(COL_TODO_CREATED_AT, System.currentTimeMillis());
        values.put(COL_TODO_UPDATED_AT, System.currentTimeMillis());

        long id = db.insert(TABLE_TODO, null, values);
        db.close();
        return id;
    }

    /**
     * Cursor에서 ToDoItem 객체로 변환하는 공통 메서드
     */
    private ToDoItem cursorToToDoItem(Cursor cursor) {
        ToDoItem todo = new ToDoItem();
        todo.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TODO_ID)));
        todo.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_TODO_TITLE)));
        
        int detailIndex = cursor.getColumnIndex(COL_TODO_DETAIL);
        todo.setDetail(detailIndex >= 0 ? cursor.getString(detailIndex) : "");
        
        int dateIndex = cursor.getColumnIndex(COL_TODO_DATE);
        todo.setDate(dateIndex >= 0 ? cursor.getLong(dateIndex) : System.currentTimeMillis());
        
        int timeIndex = cursor.getColumnIndex(COL_TODO_TIME);
        todo.setTime(timeIndex >= 0 ? cursor.getLong(timeIndex) : System.currentTimeMillis());
        
        todo.setCompleted(cursor.getInt(cursor.getColumnIndexOrThrow(COL_TODO_COMPLETED)) == 1);
        
        int checkedIndex = cursor.getColumnIndex(COL_TODO_CHECKED);
        todo.setChecked(checkedIndex >= 0 && cursor.getInt(checkedIndex) == 1);
        
        int notificationTypeIndex = cursor.getColumnIndex(COL_TODO_NOTIFICATION_TYPE);
        todo.setNotificationType(notificationTypeIndex >= 0 ? cursor.getString(notificationTypeIndex) : null);
        
        int workRequestIdIndex = cursor.getColumnIndex(COL_TODO_WORK_REQUEST_ID);
        todo.setWorkRequestId(workRequestIdIndex >= 0 ? cursor.getString(workRequestIdIndex) : null);
        
        todo.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TODO_CREATED_AT)));
        todo.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TODO_UPDATED_AT)));
        
        return todo;
    }

    /**
     * 모든 ToDo 항목을 가져옵니다 (완료/미완료 구분 없이)
     */
    public List<ToDoItem> getAllToDos() {
        List<ToDoItem> todoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String orderBy = COL_TODO_DATE + " ASC, " + COL_TODO_TIME + " ASC";
        Cursor cursor = db.query(TABLE_TODO, null, null, null, null, null, orderBy);

        if (cursor.moveToFirst()) {
            do {
                todoList.add(cursorToToDoItem(cursor));
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return todoList;
    }

    /**
     * 완료되지 않은 ToDo 항목만 가져옵니다 (위젯용)
     */
    public List<ToDoItem> getActiveToDos() {
        List<ToDoItem> todoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_TODO_COMPLETED + " IS NULL OR " + COL_TODO_COMPLETED + " = ?";
        String[] selectionArgs = {"0"};
        String orderBy = COL_TODO_DATE + " ASC, " + COL_TODO_TIME + " ASC";
        Cursor cursor = db.query(TABLE_TODO, null, selection, selectionArgs, null, null, orderBy);

        if (cursor.moveToFirst()) {
            do {
                todoList.add(cursorToToDoItem(cursor));
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return todoList;
    }

    /**
     * 완료된 ToDo 항목만 가져옵니다
     */
    public List<ToDoItem> getCompletedToDos() {
        List<ToDoItem> todoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_TODO_COMPLETED + " = ?";
        String[] selectionArgs = {"1"};
        String orderBy = COL_TODO_DATE + " ASC, " + COL_TODO_TIME + " ASC";
        Cursor cursor = db.query(TABLE_TODO, null, selection, selectionArgs, null, null, orderBy);

        if (cursor.moveToFirst()) {
            do {
                todoList.add(cursorToToDoItem(cursor));
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return todoList;
    }

    public int updateToDo(ToDoItem todo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TODO_TITLE, todo.getTitle());
        values.put(COL_TODO_DETAIL, todo.getDetail());
        values.put(COL_TODO_DATE, todo.getDate());
        values.put(COL_TODO_TIME, todo.getTime());
        values.put(COL_TODO_COMPLETED, todo.isCompleted() ? 1 : 0);
        values.put(COL_TODO_CHECKED, todo.isChecked() ? 1 : 0);
        values.put(COL_TODO_NOTIFICATION_TYPE, todo.getNotificationType());
        values.put(COL_TODO_WORK_REQUEST_ID, todo.getWorkRequestId());
        values.put(COL_TODO_UPDATED_AT, System.currentTimeMillis());

        int result = db.update(TABLE_TODO, values, COL_TODO_ID + " = ?", new String[]{String.valueOf(todo.getId())});
        db.close();
        return result;
    }

    public int deleteToDo(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_TODO, COL_TODO_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public ToDoItem getToDoById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TODO, null, COL_TODO_ID + " = ?", 
                new String[]{String.valueOf(id)}, null, null, null);

        ToDoItem todo = null;
        if (cursor.moveToFirst()) {
            todo = cursorToToDoItem(cursor);
        }
        
        cursor.close();
        db.close();
        return todo;
    }

    // ========== Memo CRUD ==========

    public long insertMemo(MemoItem memo) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        long id = 0;
        try {
            // 최소 order 값 가져오기
            Cursor cursor = db.rawQuery("SELECT MIN(" + COL_MEMO_ORDER + ") FROM " + TABLE_MEMO, null);
            int minOrder = 0;
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                minOrder = cursor.getInt(0);
            }
            cursor.close();
            
            // 새 메모를 상단에 추가하기 위해 order를 minOrder - 1로 설정
            // 기존 메모들의 order를 +1 증가
            db.execSQL("UPDATE " + TABLE_MEMO + " SET " + COL_MEMO_ORDER + " = " + COL_MEMO_ORDER + " + 1");
            
            ContentValues values = new ContentValues();
            values.put(COL_MEMO_CONTENT, memo.getContent());
            values.put(COL_MEMO_CREATED_AT, System.currentTimeMillis());
            values.put(COL_MEMO_UPDATED_AT, System.currentTimeMillis());
            values.put(COL_MEMO_ORDER, minOrder - 1);

            id = db.insert(TABLE_MEMO, null, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
        return id;
    }

    private MemoItem cursorToMemo(Cursor cursor) {
        MemoItem memo = new MemoItem();
        memo.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEMO_ID)));
        memo.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_MEMO_CONTENT)));
        memo.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEMO_CREATED_AT)));
        memo.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEMO_UPDATED_AT)));
        int orderIndex = cursor.getColumnIndex(COL_MEMO_ORDER);
        if (orderIndex >= 0) {
            memo.setOrder(cursor.getInt(orderIndex));
        }
        int deletedAtIndex = cursor.getColumnIndex(COL_MEMO_DELETED_AT);
        if (deletedAtIndex >= 0 && !cursor.isNull(deletedAtIndex)) {
            memo.setDeletedAt(cursor.getLong(deletedAtIndex));
        }
        return memo;
    }

    public MemoItem getMemoById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        MemoItem memo = null;
        try (Cursor cursor = db.query(TABLE_MEMO, null, COL_MEMO_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                memo = cursorToMemo(cursor);
            }
        }
        db.close();
        return memo;
    }

    public List<MemoItem> getAllMemos() {
        List<MemoItem> memoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_MEMO_DELETED_AT + " IS NULL";
        Cursor cursor = db.query(TABLE_MEMO, null, selection, null, null, null, COL_MEMO_ORDER + " ASC");

        if (cursor.moveToFirst()) {
            do {
                memoList.add(cursorToMemo(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return memoList;
    }

    public int updateMemo(MemoItem memo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MEMO_CONTENT, memo.getContent());
        values.put(COL_MEMO_UPDATED_AT, System.currentTimeMillis());

        int result = db.update(TABLE_MEMO, values, COL_MEMO_ID + " = ?", new String[]{String.valueOf(memo.getId())});
        db.close();
        return result;
    }

    public int deleteMemo(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MEMO_DELETED_AT, System.currentTimeMillis());
        int result = db.update(TABLE_MEMO, values, COL_MEMO_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public List<MemoItem> getDeletedMemos() {
        List<MemoItem> memoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_MEMO_DELETED_AT + " IS NOT NULL";
        Cursor cursor = db.query(TABLE_MEMO, null, selection, null, null, null, COL_MEMO_DELETED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                memoList.add(cursorToMemo(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return memoList;
    }

    public int restoreMemo(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.putNull(COL_MEMO_DELETED_AT);
        int result = db.update(TABLE_MEMO, values, COL_MEMO_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public int permanentlyDeleteMemo(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_MEMO, COL_MEMO_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public void updateMemoOrder(List<MemoItem> memos) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < memos.size(); i++) {
                ContentValues values = new ContentValues();
                values.put(COL_MEMO_ORDER, i);
                db.update(TABLE_MEMO, values, COL_MEMO_ID + " = ?", new String[]{String.valueOf(memos.get(i).getId())});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    // ========== Note CRUD ==========

    public long insertNote(NoteItem note) {
        // 노트 내용은 항상 평문으로 저장한다. 잠금은 화면 접근 인증만 담당하고 내보내기 파일도 평문이다.
        String content = note.getContent();
        
        SQLiteDatabase db = this.getWritableDatabase();
        
        // 최소 order 값 가져오기
        Cursor cursor = db.rawQuery("SELECT MIN(" + COL_NOTE_ORDER + ") FROM " + TABLE_NOTE + " WHERE " + COL_NOTE_DELETED_AT + " IS NULL", null);
        int minOrder = 0;
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            minOrder = cursor.getInt(0);
        }
        cursor.close();
        
        // 새 노트를 상단에 추가하기 위해 order를 minOrder - 1로 설정
        // 기존 노트들의 order를 +1 증가
        db.execSQL("UPDATE " + TABLE_NOTE + " SET " + COL_NOTE_ORDER + " = " + COL_NOTE_ORDER + " + 1 WHERE " + COL_NOTE_DELETED_AT + " IS NULL");
        
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_TITLE, note.getTitle());
        values.put(COL_NOTE_CONTENT, content);
        values.put(COL_NOTE_CREATED_AT, System.currentTimeMillis());
        values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_NOTE_LOCKED, note.isLocked() ? 1 : 0);
        values.put(COL_NOTE_ORDER, minOrder - 1);

        long id = db.insert(TABLE_NOTE, null, values);
        db.close();
        return id;
    }

    private NoteItem cursorToNote(Cursor cursor) {
        NoteItem note = new NoteItem();
        note.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_ID)));
        note.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_TITLE)));
        // 노트 내용은 평문으로 저장된다. 잠긴 노트의 내용을 가리는 것은 화면의 몫이다.
        note.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_CONTENT)));
        note.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_CREATED_AT)));
        note.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_UPDATED_AT)));
        int deletedAtIndex = cursor.getColumnIndex(COL_NOTE_DELETED_AT);
        if (deletedAtIndex >= 0 && !cursor.isNull(deletedAtIndex)) {
            note.setDeletedAt(cursor.getLong(deletedAtIndex));
        }
        int lockedIndex = cursor.getColumnIndex(COL_NOTE_LOCKED);
        note.setLocked(lockedIndex >= 0 && cursor.getInt(lockedIndex) == 1);
        int orderIndex = cursor.getColumnIndex(COL_NOTE_ORDER);
        if (orderIndex >= 0) {
            note.setOrder(cursor.getInt(orderIndex));
        }
        return note;
    }

    public List<NoteItem> getAllNotes() {
        List<NoteItem> noteList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_NOTE_DELETED_AT + " IS NULL";
        Cursor cursor = db.query(TABLE_NOTE, null, selection, null, null, null, COL_NOTE_ORDER + " ASC");

        if (cursor.moveToFirst()) {
            do {
                noteList.add(cursorToNote(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return noteList;
    }

    public int updateNote(NoteItem note) {
        // 노트 내용은 항상 평문으로 저장한다. 잠금은 화면 접근 인증만 담당하고 내보내기 파일도 평문이다.
        String content = note.getContent();

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            archiveNoteVersion(db, note.getId(), note.getTitle(), content);

            ContentValues values = new ContentValues();
            values.put(COL_NOTE_TITLE, note.getTitle());
            values.put(COL_NOTE_CONTENT, content);
            values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());
            // 잠금 상태는 저장하지 않는다. 열려 있던 화면의 옛 값이 암호 제거 후 다시 잠그면 안 된다.

            int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(note.getId())});
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /** 제목이나 내용이 바뀔 때만 덮어쓰기 전 내용을 보관하고, 오래된 버전은 정리한다. */
    private static void archiveNoteVersion(SQLiteDatabase db, long noteId, String newTitle, String newContent) {
        try (Cursor cursor = db.query(TABLE_NOTE,
                new String[]{COL_NOTE_TITLE, COL_NOTE_CONTENT, COL_NOTE_UPDATED_AT},
                COL_NOTE_ID + " = ?", new String[]{String.valueOf(noteId)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            String oldTitle = cursor.getString(0);
            String oldContent = cursor.getString(1);
            if (sameText(oldTitle, newTitle) && sameText(oldContent, newContent)) {
                return;
            }
            ContentValues version = new ContentValues();
            version.put(COL_HISTORY_NOTE_ID, noteId);
            version.put(COL_HISTORY_TITLE, oldTitle);
            version.put(COL_HISTORY_CONTENT, oldContent);
            version.put(COL_HISTORY_SAVED_AT, cursor.isNull(2) ? System.currentTimeMillis() : cursor.getLong(2));
            db.insertOrThrow(TABLE_NOTE_HISTORY, null, version);
        }
        String noteIdArg = String.valueOf(noteId);
        db.delete(TABLE_NOTE_HISTORY,
                COL_HISTORY_NOTE_ID + " = ? AND " + COL_HISTORY_ID + " NOT IN (SELECT " + COL_HISTORY_ID +
                        " FROM " + TABLE_NOTE_HISTORY + " WHERE " + COL_HISTORY_NOTE_ID + " = ? ORDER BY " +
                        COL_HISTORY_ID + " DESC LIMIT " + MAX_NOTE_VERSIONS + ")",
                new String[]{noteIdArg, noteIdArg});
    }

    private static boolean sameText(String a, String b) {
        return (a != null ? a : "").equals(b != null ? b : "");
    }

    /** 최신 버전부터 반환한다. */
    public List<NoteVersion> getNoteHistory(long noteId) {
        List<NoteVersion> versions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_NOTE_HISTORY, null, COL_HISTORY_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)}, null, null, COL_HISTORY_ID + " DESC")) {
            while (cursor.moveToNext()) {
                versions.add(new NoteVersion(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_HISTORY_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_HISTORY_NOTE_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_CONTENT)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_HISTORY_SAVED_AT))));
            }
        }
        db.close();
        return versions;
    }

    public int deleteNoteHistory(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_NOTE_HISTORY, COL_HISTORY_NOTE_ID + " = ?", new String[]{String.valueOf(noteId)});
        db.close();
        return result;
    }

    public int deleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_NOTE_DELETED_AT, System.currentTimeMillis());
            int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
            removeDraft(db, DRAFT_TYPE_NOTE, id);
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public List<NoteItem> getDeletedNotes() {
        List<NoteItem> noteList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_NOTE_DELETED_AT + " IS NOT NULL";
        Cursor cursor = db.query(TABLE_NOTE, null, selection, null, null, null, COL_NOTE_DELETED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                noteList.add(cursorToNote(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return noteList;
    }

    public int restoreNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.putNull(COL_NOTE_DELETED_AT);
        int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public int permanentlyDeleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            int result = db.delete(TABLE_NOTE, COL_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
            removeDraft(db, DRAFT_TYPE_NOTE, id);
            db.delete(TABLE_NOTE_HISTORY, COL_HISTORY_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * 휴지통의 모든 항목을 영구 삭제
     * @return 삭제된 항목의 총 개수
     */
    public int emptyTrash() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            int deletedMemos = db.delete(TABLE_MEMO, COL_MEMO_DELETED_AT + " IS NOT NULL", null);
            // 영구 삭제한 노트의 이전 내용과 임시 저장본도 함께 지운다.
            String trashedNoteIds = "(SELECT " + COL_NOTE_ID + " FROM " + TABLE_NOTE + " WHERE " +
                    COL_NOTE_DELETED_AT + " IS NOT NULL)";
            db.delete(TABLE_NOTE_HISTORY, COL_HISTORY_NOTE_ID + " IN " + trashedNoteIds, null);
            db.delete(TABLE_DRAFT, COL_DRAFT_TYPE + " = ? AND " + COL_DRAFT_TARGET_ID + " IN " + trashedNoteIds,
                    new String[]{DRAFT_TYPE_NOTE});
            int deletedNotes = db.delete(TABLE_NOTE, COL_NOTE_DELETED_AT + " IS NOT NULL", null);
            int deletedMessages = db.delete(TABLE_MESSAGE, COL_MESSAGE_DELETED_AT + " IS NOT NULL", null);
            db.setTransactionSuccessful();
            return deletedMemos + deletedNotes + deletedMessages;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public NoteItem getNoteById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NOTE, null, COL_NOTE_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        NoteItem note = null;
        if (cursor.moveToFirst()) {
            note = cursorToNote(cursor);
        }

        cursor.close();
        db.close();
        return note;
    }

    // ========== Note Lock & Password ==========

    /**
     * 암호와 힌트를 함께 저장합니다.
     */
    public void setNotePassword(String password, String hint) {
        SQLiteDatabase db = this.getWritableDatabase();
        String hash = hashPassword(password);

        // 기존 암호가 있으면 업데이트, 없으면 삽입
        ContentValues values = new ContentValues();
        values.put(COL_PASSWORD_HASH, hash);
        if (hint != null) {
            values.put(COL_PASSWORD_HINT, hint);
        }

        // 기존 레코드 확인
        Cursor cursor = db.query(TABLE_PASSWORD, null, null, null, null, null, null);
        if (cursor.moveToFirst()) {
            // 기존 레코드가 있으면 업데이트
            db.update(TABLE_PASSWORD, values, null, null);
        } else {
            // 기존 레코드가 없으면 삽입
            db.insert(TABLE_PASSWORD, null, values);
        }
        cursor.close();
        db.close();
    }

    /**
     * 저장된 암호 해시를 반환합니다.
     */
    public String getNotePasswordHash() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PASSWORD, new String[]{COL_PASSWORD_HASH}, null, null, null, null, null);
        
        String hash = null;
        if (cursor.moveToFirst()) {
            hash = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HASH));
        }
        cursor.close();
        db.close();
        return hash;
    }

    /**
     * 암호가 설정되어 있는지 확인합니다.
     */
    public boolean hasNotePassword() {
        return getNotePasswordHash() != null;
    }

    /**
     * 저장된 암호 힌트를 반환합니다.
     */
    public String getNotePasswordHint() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PASSWORD, new String[]{COL_PASSWORD_HINT}, null, null, null, null, null);

        String hint = null;
        if (cursor.moveToFirst()) {
            hint = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HINT));
        }
        cursor.close();
        db.close();
        return hint;
    }

    /**
     * 암호 힌트만 업데이트합니다.
     */
    public void setNotePasswordHint(String hint) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PASSWORD_HINT, hint);

        // 기존 레코드 확인
        Cursor cursor = db.query(TABLE_PASSWORD, null, null, null, null, null, null);
        if (cursor.moveToFirst()) {
            // 기존 레코드가 있으면 업데이트
            db.update(TABLE_PASSWORD, values, null, null);
        } else {
            // 기존 레코드가 없으면 삽입
            db.insert(TABLE_PASSWORD, null, values);
        }
        cursor.close();
        db.close();
    }

    /**
     * 입력한 암호가 올바른지 확인합니다.
     */
    public boolean verifyNotePassword(String password) {
        String storedHash = getNotePasswordHash();
        return storedHash != null && matchesStoredHash(storedHash, password);
    }

    /**
     * 암호를 제거하고 휴지통을 포함한 모든 노트의 잠금을 해제합니다.
     * 암호 없이 잠긴 노트가 남으면 그 노트를 열 수 없게 됩니다.
     */
    public void removeNotePassword() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_NOTE_LOCKED, 0);
            db.update(TABLE_NOTE, values, COL_NOTE_LOCKED + " = 1", null);
            db.delete(TABLE_PASSWORD, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * 암호를 PBKDF2로 해시합니다 (Salt 포함).
     */
    private String hashPassword(String password) {
        try {
            // Salt 생성 (16바이트)
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            // PBKDF2 해시 생성
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
            SecretKey key = factory.generateSecret(spec);
            byte[] hash = key.getEncoded();

            // Salt(16) + Hash(32) 결합하여 저장
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 아래 암호화 메서드는 현재 호출되지 않는다. 1.6 이전에 잠근 노트는 본문이 암호문으로 저장되어 있어,
    // 그런 노트를 복구해야 할 때를 대비해 남겨 둔다.

    /**
     * 암호화 키를 생성합니다 (PBKDF2 + Salt 사용).
     */
    private SecretKeySpec getEncryptionKey() {
        String passwordHash = getNotePasswordHash();
        return getEncryptionKey(passwordHash);
    }

    /**
     * 암호 해시로부터 암호화 키를 생성합니다 (DB를 열지 않음).
     */
    private SecretKeySpec getEncryptionKey(String passwordHash) {
        if (passwordHash == null) {
            return null;
        }

        try {
            byte[] hashBytes = Base64.decode(passwordHash, Base64.NO_WRAP);

            // 새로운 포맷 (Salt 16 + PBKDF2 32 = 48바이트)
            if (hashBytes.length == 48) {
                byte[] salt = new byte[16];
                byte[] pbkdf2Hash = new byte[32];
                System.arraycopy(hashBytes, 0, salt, 0, 16);
                System.arraycopy(hashBytes, 16, pbkdf2Hash, 0, 32);

                return deriveAesKey(pbkdf2Hash, salt);
            } else {
                // 하위 호환성: 기존 SHA-256 해시 방식
                byte[] keyBytes = new byte[16];
                System.arraycopy(hashBytes, 0, keyBytes, 0, Math.min(16, hashBytes.length));
                return new SecretKeySpec(keyBytes, "AES");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * PBKDF2를 사용하여 AES 키를 파생합니다.
     */
    private SecretKeySpec deriveAesKey(byte[] passwordBytes, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(new String(passwordBytes, "UTF-8").toCharArray(), salt, 10000, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * 텍스트를 AES/CBC/PKCS5Padding으로 암호화합니다.
     */
    private String encryptContent(String content) {
        String passwordHash = getNotePasswordHash();
        return encryptContent(content, passwordHash);
    }

    /**
     * 텍스트를 AES/CBC/PKCS5Padding으로 암호화합니다 (암호 해시를 매개변수로 받음).
     */
    private String encryptContent(String content, String passwordHash) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        try {
            SecretKeySpec keySpec = getEncryptionKey(passwordHash);
            if (keySpec == null) {
                return content; // 암호가 설정되지 않았으면 암호화하지 않음
            }

            // IV 생성 (16바이트) - 매번 랜덤하게 생성
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // AES/CBC 암호화 - 사용자 패스워드에서 파생된 고정 키 사용
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(content.getBytes("UTF-8"));

            // IV(16) + 암호문 결합
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    /**
     * 암호화된 텍스트를 복호화합니다.
     */
    private String decryptContent(String encryptedContent) {
        String passwordHash = getNotePasswordHash();
        return decryptContent(encryptedContent, passwordHash);
    }

    /**
     * 암호화된 텍스트를 복호화합니다 (암호 해시를 매개변수로 받음).
     */
    private String decryptContent(String encryptedContent, String passwordHash) {
        if (encryptedContent == null || encryptedContent.isEmpty()) {
            return encryptedContent;
        }
        try {
            SecretKeySpec keySpec = getEncryptionKey(passwordHash);
            if (keySpec == null) {
                return encryptedContent; // 암호가 설정되지 않았으면 복호화하지 않음
            }

            // Base64 디코딩
            byte[] combined = Base64.decode(encryptedContent, Base64.NO_WRAP);

            // 하위 호환성: 기존 형식 (Salt + IV + 암호문) 또는 새로운 형식 (IV + 암호문) 확인
            byte[] iv;
            byte[] encrypted;
            
            if (combined.length >= 32 && combined.length % 16 == 0) {
                // 기존 형식 (Salt 16 + IV 16 + 암호문) - 하위 호환성 처리
                try {
                    byte[] salt = new byte[16];
                    iv = new byte[16];
                    encrypted = new byte[combined.length - 32];
                    
                    System.arraycopy(combined, 0, salt, 0, 16);
                    System.arraycopy(combined, 16, iv, 0, 16);
                    System.arraycopy(combined, 32, encrypted, 0, encrypted.length);
                    
                    // 기존 방식으로 복호화 시도 (하위 호환성)
                    // 하지만 키는 고정 키를 사용
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    IvParameterSpec ivSpec = new IvParameterSpec(iv);
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                    byte[] decrypted = cipher.doFinal(encrypted);
                    return new String(decrypted, "UTF-8");
                } catch (Exception e) {
                    // 기존 형식으로 복호화 실패 시 새로운 형식으로 시도
                }
            }
            
            // 새로운 형식 (IV 16 + 암호문)
            if (combined.length < 16) {
                return encryptedContent; // 너무 짧은 데이터
            }
            
            iv = new byte[16];
            encrypted = new byte[combined.length - 16];
            
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

            // AES/CBC 복호화 - 암호화 시와 동일한 고정 키 사용
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            // 복호화 실패 시 원본 반환 (하위 호환성)
            android.util.Log.e("DatabaseHelper", "복호화 실패", e);
            return encryptedContent;
        }
    }

    /**
     * 노트를 잠금/잠금해제합니다.
     * 잠금은 화면 접근 인증만 담당하며 내용은 평문 그대로 유지됩니다.
     */
    public int toggleNoteLock(long noteId, boolean lock) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // DB 업데이트 - 잠금 상태만 변경, 내용은 그대로 유지
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_LOCKED, lock ? 1 : 0);
        values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());
        
        int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(noteId)});
        db.close();
        return result;
    }

    /**
     * 노트 순서를 업데이트합니다.
     */
    public void updateNoteOrder(List<NoteItem> notes) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < notes.size(); i++) {
                ContentValues values = new ContentValues();
                values.put(COL_NOTE_ORDER, i);
                db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(notes.get(i).getId())});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    // ========== Draft (저장하지 않은 편집 내용) ==========

    public void saveNoteDraft(long noteId, String title, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        writeDraft(db, DRAFT_TYPE_NOTE, noteId, title, content);
        db.close();
    }

    public DraftItem getNoteDraft(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        DraftItem draft = readDraft(db, COL_DRAFT_TYPE + " = ? AND " + COL_DRAFT_TARGET_ID + " = ?",
                new String[]{DRAFT_TYPE_NOTE, String.valueOf(noteId)});
        db.close();
        return draft;
    }

    public void deleteNoteDraft(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        removeDraft(db, DRAFT_TYPE_NOTE, noteId);
        db.close();
    }

    /** 메모 입력창은 하나뿐이므로 임시 저장본도 하나만 유지한다. */
    public void saveMemoDraft(long memoId, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_DRAFT, COL_DRAFT_TYPE + " = ?", new String[]{DRAFT_TYPE_MEMO});
            writeDraft(db, DRAFT_TYPE_MEMO, memoId, null, content);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public DraftItem getMemoDraft() {
        SQLiteDatabase db = this.getReadableDatabase();
        DraftItem draft = readDraft(db, COL_DRAFT_TYPE + " = ?", new String[]{DRAFT_TYPE_MEMO});
        db.close();
        return draft;
    }

    public void deleteMemoDraft() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DRAFT, COL_DRAFT_TYPE + " = ?", new String[]{DRAFT_TYPE_MEMO});
        db.close();
    }

    private static void writeDraft(SQLiteDatabase db, String type, long targetId, String title, String content) {
        ContentValues values = new ContentValues();
        values.put(COL_DRAFT_TYPE, type);
        values.put(COL_DRAFT_TARGET_ID, targetId);
        values.put(COL_DRAFT_TITLE, title);
        values.put(COL_DRAFT_CONTENT, content);
        values.put(COL_DRAFT_SAVED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_DRAFT, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static DraftItem readDraft(SQLiteDatabase db, String selection, String[] args) {
        try (Cursor cursor = db.query(TABLE_DRAFT, null, selection, args, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new DraftItem(
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_DRAFT_TARGET_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_DRAFT_TITLE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_DRAFT_CONTENT)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_DRAFT_SAVED_AT)));
        }
    }

    private static void removeDraft(SQLiteDatabase db, String type, long targetId) {
        db.delete(TABLE_DRAFT, COL_DRAFT_TYPE + " = ? AND " + COL_DRAFT_TARGET_ID + " = ?",
                new String[]{type, String.valueOf(targetId)});
    }

    // ========== Message CRUD (FCM 알림) ==========

    public long insertMessage(kr.akotis.todonote.model.MessageItem message) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_MESSAGE_TITLE, message.getTitle());
            values.put(COL_MESSAGE_BODY, message.getBody());
            values.put(COL_MESSAGE_RECEIVED_AT, message.getReceivedAt());
            values.put(COL_MESSAGE_IS_READ, message.isRead() ? 1 : 0);

            long id = db.insertOrThrow(TABLE_MESSAGE, null, values);
            trimMessages(db);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public List<kr.akotis.todonote.model.MessageItem> getAllMessages() {
        List<kr.akotis.todonote.model.MessageItem> messageList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_MESSAGE_DELETED_AT + " IS NULL";
        Cursor cursor = db.query(TABLE_MESSAGE, null, selection, null, null, null,
                COL_MESSAGE_RECEIVED_AT + " DESC, " + COL_MESSAGE_ID + " DESC",
                String.valueOf(MAX_STORED_MESSAGES));

        if (cursor.moveToFirst()) {
            do {
                try {
                    kr.akotis.todonote.model.MessageItem message = new kr.akotis.todonote.model.MessageItem();
                    message.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MESSAGE_ID)));
                    message.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_TITLE)));
                    message.setBody(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_BODY)));
                    message.setReceivedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MESSAGE_RECEIVED_AT)));
                    message.setRead(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MESSAGE_IS_READ)) == 1);
                    int deletedAtIndex = cursor.getColumnIndex(COL_MESSAGE_DELETED_AT);
                    if (deletedAtIndex >= 0 && !cursor.isNull(deletedAtIndex)) {
                        message.setDeletedAt(cursor.getLong(deletedAtIndex));
                    }
                    messageList.add(message);
                } catch (Exception e) {
                    android.util.Log.e("DatabaseHelper", "Error loading message", e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return messageList;
    }

    public int getUnreadMessageCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MESSAGE + 
                " WHERE " + COL_MESSAGE_IS_READ + " = 0 AND " + COL_MESSAGE_DELETED_AT + " IS NULL", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }

    public void markMessageAsRead(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_IS_READ, 1);
        db.update(TABLE_MESSAGE, values, COL_MESSAGE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void markAllMessagesAsRead() {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_IS_READ, 1);
        String whereClause = COL_MESSAGE_DELETED_AT + " IS NULL";
        db.update(TABLE_MESSAGE, values, whereClause, null);
        db.close();
    }

    public int deleteMessage(long id) {
        // 휴지통으로 이동 (영구 삭제가 아님)
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_DELETED_AT, System.currentTimeMillis());
        int result = db.update(TABLE_MESSAGE, values, COL_MESSAGE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public List<kr.akotis.todonote.model.MessageItem> getDeletedMessages() {
        List<kr.akotis.todonote.model.MessageItem> messageList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_MESSAGE_DELETED_AT + " IS NOT NULL";
        Cursor cursor = db.query(TABLE_MESSAGE, null, selection, null, null, null, 
                COL_MESSAGE_DELETED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                kr.akotis.todonote.model.MessageItem message = new kr.akotis.todonote.model.MessageItem();
                message.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MESSAGE_ID)));
                message.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_TITLE)));
                message.setBody(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_BODY)));
                message.setReceivedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_MESSAGE_RECEIVED_AT)));
                message.setRead(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MESSAGE_IS_READ)) == 1);
                int deletedAtIndex = cursor.getColumnIndex(COL_MESSAGE_DELETED_AT);
                if (deletedAtIndex >= 0 && !cursor.isNull(deletedAtIndex)) {
                    message.setDeletedAt(cursor.getLong(deletedAtIndex));
                }
                messageList.add(message);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return messageList;
    }

    public int restoreMessage(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.putNull(COL_MESSAGE_DELETED_AT);
        int result = db.update(TABLE_MESSAGE, values, COL_MESSAGE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public int permanentlyDeleteMessage(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_MESSAGE, COL_MESSAGE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }
}

