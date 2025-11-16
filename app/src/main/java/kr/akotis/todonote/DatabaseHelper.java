package kr.akotis.todonote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ToDoNote.db";
    private static final int DATABASE_VERSION = 3;

    // ToDo 테이블
    private static final String TABLE_TODO = "todo";
    private static final String COL_TODO_ID = "id";
    private static final String COL_TODO_TITLE = "title";
    private static final String COL_TODO_DETAIL = "detail";
    private static final String COL_TODO_DATE = "date";
    private static final String COL_TODO_TIME = "time";
    private static final String COL_TODO_COMPLETED = "completed";
    private static final String COL_TODO_CREATED_AT = "created_at";
    private static final String COL_TODO_UPDATED_AT = "updated_at";

    // Memo 테이블
    private static final String TABLE_MEMO = "memo";
    private static final String COL_MEMO_ID = "id";
    private static final String COL_MEMO_CONTENT = "content";
    private static final String COL_MEMO_CREATED_AT = "created_at";
    private static final String COL_MEMO_UPDATED_AT = "updated_at";
    private static final String COL_MEMO_ORDER = "memo_order";

    // Note 테이블
    private static final String TABLE_NOTE = "note";
    private static final String COL_NOTE_ID = "id";
    private static final String COL_NOTE_TITLE = "title";
    private static final String COL_NOTE_CONTENT = "content";
    private static final String COL_NOTE_CREATED_AT = "created_at";
    private static final String COL_NOTE_UPDATED_AT = "updated_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
                COL_TODO_CREATED_AT + " INTEGER, " +
                COL_TODO_UPDATED_AT + " INTEGER)";
        db.execSQL(createTodoTable);

        // Memo 테이블 생성
        String createMemoTable = "CREATE TABLE " + TABLE_MEMO + " (" +
                COL_MEMO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MEMO_CONTENT + " TEXT NOT NULL, " +
                COL_MEMO_CREATED_AT + " INTEGER, " +
                COL_MEMO_UPDATED_AT + " INTEGER, " +
                COL_MEMO_ORDER + " INTEGER DEFAULT 0)";
        db.execSQL(createMemoTable);

        // Note 테이블 생성
        String createNoteTable = "CREATE TABLE " + TABLE_NOTE + " (" +
                COL_NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NOTE_TITLE + " TEXT, " +
                COL_NOTE_CONTENT + " TEXT NOT NULL, " +
                COL_NOTE_CREATED_AT + " INTEGER, " +
                COL_NOTE_UPDATED_AT + " INTEGER)";
        db.execSQL(createNoteTable);
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
                    COL_TODO_CREATED_AT + " INTEGER, " +
                    COL_TODO_UPDATED_AT + " INTEGER)";
            db.execSQL(createTodoTable);
            // 기존 데이터 마이그레이션 (content를 title로)
            db.execSQL("INSERT INTO " + TABLE_TODO + " (" +
                    COL_TODO_ID + ", " + COL_TODO_TITLE + ", " + COL_TODO_DETAIL + ", " +
                    COL_TODO_DATE + ", " + COL_TODO_TIME + ", " + COL_TODO_COMPLETED + ", " +
                    COL_TODO_CREATED_AT + ", " + COL_TODO_UPDATED_AT + ") " +
                    "SELECT " + COL_TODO_ID + ", content, '', " +
                    System.currentTimeMillis() + ", " + System.currentTimeMillis() + ", " +
                    COL_TODO_COMPLETED + ", " + COL_TODO_CREATED_AT + ", " + COL_TODO_UPDATED_AT +
                    " FROM " + TABLE_TODO + "_old");
            // 백업 테이블 삭제
            db.execSQL("DROP TABLE " + TABLE_TODO + "_old");
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
        values.put(COL_TODO_CREATED_AT, System.currentTimeMillis());
        values.put(COL_TODO_UPDATED_AT, System.currentTimeMillis());

        long id = db.insert(TABLE_TODO, null, values);
        db.close();
        return id;
    }

    public List<ToDoItem> getAllToDos() {
        List<ToDoItem> todoList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        // 날짜 순으로 정렬 (날짜가 빠른 순서대로, 날짜가 같으면 시간 순)
        Cursor cursor = db.query(TABLE_TODO, null, null, null, null, null, 
                COL_TODO_DATE + " ASC, " + COL_TODO_TIME + " ASC");

        if (cursor.moveToFirst()) {
            do {
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
                todo.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TODO_CREATED_AT)));
                todo.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TODO_UPDATED_AT)));
                todoList.add(todo);
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
        Cursor cursor = db.query(TABLE_MEMO, null, null, null, null, null, COL_MEMO_ORDER + " ASC");

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
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_TITLE, note.getTitle());
        values.put(COL_NOTE_CONTENT, note.getContent());
        values.put(COL_NOTE_CREATED_AT, System.currentTimeMillis());
        values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());

        long id = db.insert(TABLE_NOTE, null, values);
        db.close();
        return id;
    }

    public List<NoteItem> getAllNotes() {
        List<NoteItem> noteList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NOTE, null, null, null, null, null, COL_NOTE_CREATED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                NoteItem note = new NoteItem();
                note.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_ID)));
                note.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_TITLE)));
                note.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE_CONTENT)));
                note.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_CREATED_AT)));
                note.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_NOTE_UPDATED_AT)));
                noteList.add(note);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return noteList;
    }

    public int updateNote(NoteItem note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_TITLE, note.getTitle());
        values.put(COL_NOTE_CONTENT, note.getContent());
        values.put(COL_NOTE_UPDATED_AT, System.currentTimeMillis());

        int result = db.update(TABLE_NOTE, values, COL_NOTE_ID + " = ?", new String[]{String.valueOf(note.getId())});
        db.close();
        return result;
    }

    public int deleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_NOTE, COL_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return result;
    }
}

