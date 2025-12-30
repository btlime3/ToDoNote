package kr.akotis.todonote.database;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;

import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.model.ToDoItem;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ToDoNote.db";
    private static final int DATABASE_VERSION = 12;
    
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

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    /**
     * 데이터베이스 파일의 절대 경로를 반환합니다.
     */
    public String getDatabasePath() {
        return getReadableDatabase().getPath();
    }

    /**
     * 데이터베이스 파일의 절대 경로를 반환합니다 (Context를 통해).
     */
    public static String getDatabasePath(Context context) {
        return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
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

    public List<MemoItem> getAllMemos() {
        List<MemoItem> memoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_MEMO_DELETED_AT + " IS NULL";
        Cursor cursor = db.query(TABLE_MEMO, null, selection, null, null, null, COL_MEMO_ORDER + " ASC");

        if (cursor.moveToFirst()) {
            do {
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
                memoList.add(memo);
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
                memoList.add(memo);
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
        // DB를 열기 전에 hasNotePassword()를 먼저 호출
        boolean needEncryption = note.isLocked() && hasNotePassword();
        String content = note.getContent();
        if (needEncryption) {
            content = encryptContent(content);
        }
        
        // 이제 DB를 열어서 삽입
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

    public List<NoteItem> getAllNotes() {
        List<NoteItem> noteList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_NOTE_DELETED_AT + " IS NULL";
        Cursor cursor = db.query(TABLE_NOTE, null, selection, null, null, null, COL_NOTE_ORDER + " ASC");

        if (cursor.moveToFirst()) {
            do {
                NoteItem note = new NoteItem();
                note.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_ID)));
                note.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_TITLE)));
                note.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_CONTENT)));
                note.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_CREATED_AT)));
                note.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_UPDATED_AT)));
                int deletedAtIndex = cursor.getColumnIndex(COL_NOTE_DELETED_AT);
                if (deletedAtIndex >= 0 && !cursor.isNull(deletedAtIndex)) {
                    note.setDeletedAt(cursor.getLong(deletedAtIndex));
                }
                int lockedIndex = cursor.getColumnIndex(COL_NOTE_LOCKED);
                if (lockedIndex >= 0) {
                    note.setLocked(cursor.getInt(lockedIndex) == 1);
                }
                int orderIndex = cursor.getColumnIndex(COL_NOTE_ORDER);
                if (orderIndex >= 0) {
                    note.setOrder(cursor.getInt(orderIndex));
                }
                noteList.add(note);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return noteList;
    }

    public int updateNote(NoteItem note) {
        // DB를 열기 전에 hasNotePassword()를 먼저 호출
        boolean needEncryption = note.isLocked() && hasNotePassword();
        String content = note.getContent();
        if (needEncryption) {
            content = encryptContent(content);
        }
        
        // 이제 DB를 열어서 업데이트
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_TITLE, note.getTitle());
        values.put(COL_NOTE_CONTENT, content);
        values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_NOTE_LOCKED, note.isLocked() ? 1 : 0);

        int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(note.getId())});
        db.close();
        return result;
    }

    public int deleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_DELETED_AT, System.currentTimeMillis());
        int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public List<NoteItem> getDeletedNotes() {
        List<NoteItem> noteList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_NOTE_DELETED_AT + " IS NOT NULL";
        Cursor cursor = db.query(TABLE_NOTE, null, selection, null, null, null, COL_NOTE_DELETED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                NoteItem note = new NoteItem();
                note.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_ID)));
                note.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_TITLE)));
                
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_CONTENT));
                int lockedIndex = cursor.getColumnIndex(COL_NOTE_LOCKED);
                boolean isLocked = lockedIndex >= 0 && cursor.getInt(lockedIndex) == 1;
                
                // 휴지통에서는 잠금된 노트의 내용을 복호화하지 않음 (보안 유지)
                // 암호화된 상태로 유지하여 휴지통에서 내용이 보이지 않도록 함
                note.setContent(content);
                
                note.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_CREATED_AT)));
                note.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_UPDATED_AT)));
                int deletedAtIndex = cursor.getColumnIndex(COL_NOTE_DELETED_AT);
                if (deletedAtIndex >= 0 && !cursor.isNull(deletedAtIndex)) {
                    note.setDeletedAt(cursor.getLong(deletedAtIndex));
                }
                note.setLocked(isLocked);
                noteList.add(note);
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
        int result = db.delete(TABLE_NOTE, COL_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    /**
     * 휴지통의 모든 항목을 영구 삭제
     * @return 삭제된 항목의 총 개수
     */
    public int emptyTrash() {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedMemos = db.delete(TABLE_MEMO, COL_MEMO_DELETED_AT + " IS NOT NULL", null);
        int deletedNotes = db.delete(TABLE_NOTE, COL_NOTE_DELETED_AT + " IS NOT NULL", null);
        db.close();
        return deletedMemos + deletedNotes;
    }

    public NoteItem getNoteById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NOTE, null, COL_NOTE_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        NoteItem note = null;
        if (cursor.moveToFirst()) {
            note = new NoteItem();
            note.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_ID)));
            note.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_TITLE)));
            
            String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_CONTENT));
            int lockedIndex = cursor.getColumnIndex(COL_NOTE_LOCKED);
            boolean isLocked = lockedIndex >= 0 && cursor.getInt(lockedIndex) == 1;
            
            // 잠금된 노트는 내용을 복호화 (암호가 설정되어 있고 잠금 상태인 경우)
            if (isLocked && hasNotePassword()) {
                content = decryptContent(content);
            }
            note.setContent(content);
            
            note.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_CREATED_AT)));
            note.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_UPDATED_AT)));
            note.setLocked(isLocked);
        }

        cursor.close();
        db.close();
        return note;
    }

    // ========== Note Lock & Password ==========

    /**
     * 암호를 해시하여 데이터베이스에 저장합니다.
     */
    public void setNotePassword(String password) {
        setNotePassword(password, null);
    }

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
        if (storedHash == null) {
            return false;
        }

        try {
            byte[] storedCombined = Base64.decode(storedHash, Base64.NO_WRAP);

            // 새로운 PBKDF2 포맷 확인 (Salt 16 + PBKDF2 32 = 48바이트)
            if (storedCombined.length == 48) {
                // 새로운 PBKDF2 방식
                byte[] salt = new byte[16];
                byte[] storedPBKDF2 = new byte[32];

                System.arraycopy(storedCombined, 0, salt, 0, 16);
                System.arraycopy(storedCombined, 16, storedPBKDF2, 0, 32);

                // 입력 암호로 동일 Salt로 PBKDF2 해시 생성
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
                SecretKey key = factory.generateSecret(spec);
                byte[] inputHash = key.getEncoded();

                return java.util.Arrays.equals(storedPBKDF2, inputHash);
            } else {
                // 하위 호환성: 기존 SHA-256 방식
                String inputHash = hashPassword(password);
                return storedHash.equals(inputHash);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 암호를 제거합니다.
     */
    public void removeNotePassword() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PASSWORD, null, null);
        db.close();
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

    /**
     * 암호화 키를 생성합니다 (PBKDF2 + Salt 사용).
     */
    private SecretKeySpec getEncryptionKey() {
        String passwordHash = getNotePasswordHash();
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
        if (content == null || content.isEmpty()) {
            return content;
        }
        try {
            SecretKeySpec keySpec = getEncryptionKey();
            if (keySpec == null) {
                return content; // 암호가 설정되지 않았으면 암호화하지 않음
            }

            // 랜덤 Salt 생성 (16바이트)
            byte[] salt = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);

            // PBKDF2로 키 파생
            SecretKeySpec aesKey = deriveAesKey(content.getBytes("UTF-8"), salt);

            // IV 생성 (16바이트)
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // AES/CBC 암호화
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);
            byte[] encrypted = cipher.doFinal(content.getBytes("UTF-8"));

            // Salt(16) + IV(16) + 암호문 결합
            byte[] combined = new byte[salt.length + iv.length + encrypted.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(encrypted, 0, combined, salt.length + iv.length, encrypted.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    /**
     * 암호화된 텍스트를 복호화합니다.
     */
    private String decryptContent(String encryptedContent) {
        if (encryptedContent == null || encryptedContent.isEmpty()) {
            return encryptedContent;
        }
        try {
            SecretKeySpec keySpec = getEncryptionKey();
            if (keySpec == null) {
                return encryptedContent; // 암호가 설정되지 않았으면 복호화하지 않음
            }

            // Base64 디코딩
            byte[] combined = Base64.decode(encryptedContent, Base64.NO_WRAP);

            // Salt(16) + IV(16) + 암호문 분리
            byte[] salt = new byte[16];
            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - 32];

            System.arraycopy(combined, 0, salt, 0, 16);
            System.arraycopy(combined, 16, iv, 0, 16);
            System.arraycopy(combined, 32, encrypted, 0, encrypted.length);

            // PBKDF2로 키 재생성 (동일 Salt 사용)
            SecretKeySpec aesKey = deriveAesKey(keySpec.getEncoded(), salt);

            // AES/CBC 복호화
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            // 복호화 실패 시 원본 반환 (하위 호환성)
            return encryptedContent;
        }
    }

    /**
     * 노트를 잠금/잠금해제합니다.
     */
    public int toggleNoteLock(long noteId, boolean lock) {
        // getNoteById()를 먼저 호출 (내부에서 DB를 열고 닫음)
        NoteItem note = getNoteById(noteId);
        if (note == null) {
            return 0;
        }
        
        // 잠금 상태 변경 시 내용 암호화/복호화 처리
        String content = note.getContent();
        if (lock && hasNotePassword()) {
            // 잠금: 내용 암호화
            content = encryptContent(content);
        }
        // 잠금 해제: 이미 getNoteById에서 복호화되어 있으므로 그대로 저장
        
        // 이제 DB를 열어서 업데이트
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_LOCKED, lock ? 1 : 0);
        values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_NOTE_CONTENT, content);
        
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

    // ========== Message CRUD (FCM 알림) ==========

    public long insertMessage(kr.akotis.todonote.model.MessageItem message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_TITLE, message.getTitle());
        values.put(COL_MESSAGE_BODY, message.getBody());
        values.put(COL_MESSAGE_RECEIVED_AT, message.getReceivedAt());
        values.put(COL_MESSAGE_IS_READ, message.isRead() ? 1 : 0);

        long id = db.insert(TABLE_MESSAGE, null, values);
        db.close();
        return id;
    }

    public List<kr.akotis.todonote.model.MessageItem> getAllMessages() {
        List<kr.akotis.todonote.model.MessageItem> messageList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_MESSAGE_DELETED_AT + " IS NULL";
        Cursor cursor = db.query(TABLE_MESSAGE, null, selection, null, null, null,
                COL_MESSAGE_RECEIVED_AT + " DESC");

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

    public int deleteAllMessages() {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_MESSAGE, null, null);
        db.close();
        return result;
    }
}

