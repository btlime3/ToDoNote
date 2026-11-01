package kr.akotis.todonote.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.DefaultDatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Android 기본 처리는 손상된 DB 파일을 그대로 삭제한다.
 * 삭제되기 전에 사본을 남겨 두어 나중에 내용을 살릴 기회를 만든다.
 */
public final class DamagedDatabaseKeeper implements DatabaseErrorHandler {
    private static final String TAG = "DamagedDatabaseKeeper";
    private static final String PREFS = "damaged_database";
    private static final String KEY_PENDING_COPY = "pending_copy";
    private static final String DIRECTORY = "damaged_db";
    private static final int MAX_KEPT_COPIES = 3;
    private static final String[] FILE_SUFFIXES = {"", "-wal", "-journal"};

    private final Context context;
    private final DatabaseErrorHandler defaultHandler = new DefaultDatabaseErrorHandler();

    DamagedDatabaseKeeper(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCorruption(SQLiteDatabase db) {
        try {
            keepCopy(db.getPath());
        } catch (IOException | RuntimeException e) {
            // 사본 저장 실패가 앱 실행까지 막아서는 안 된다.
            Log.e(TAG, "Could not keep a copy of the damaged database", e);
        }
        defaultHandler.onCorruption(db);
    }

    private void keepCopy(String databasePath) throws IOException {
        File source = new File(databasePath);
        if (!source.isFile()) {
            return;
        }
        File directory = directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
        String copyName = "ToDoNote_damaged_" + timestamp + ".db";
        for (String suffix : FILE_SUFFIXES) {
            File file = new File(databasePath + suffix);
            // 열기에 실패하면서 생긴 빈 부속 파일은 담긴 내용이 없으므로 복사하지 않는다.
            if (file.isFile() && file.length() > 0) {
                File target = new File(directory, copyName + suffix);
                try {
                    Files.copy(file.toPath(), target.toPath());
                } catch (IOException e) {
                    // 저장 공간이 부족해도 곧 삭제될 원본을 옮기는 것은 가능하다.
                    target.delete();
                    if (!file.renameTo(target)) {
                        throw e;
                    }
                }
            }
        }
        removeOldCopies(directory);
        // 곧이어 파일이 삭제되고 앱이 종료될 수 있으므로 바로 기록한다.
        prefs(context).edit().putString(KEY_PENDING_COPY, copyName).commit();
    }

    private static void removeOldCopies(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".db"));
        if (files == null || files.length <= MAX_KEPT_COPIES) {
            return;
        }
        // 이름에 시각이 들어 있어 이름순이 곧 시간순이다.
        Arrays.sort(files);
        for (int i = 0; i < files.length - MAX_KEPT_COPIES; i++) {
            for (String suffix : FILE_SUFFIXES) {
                new File(files[i].getPath() + suffix).delete();
            }
        }
    }

    /** 사용자에게 아직 알리지 않은 사본 파일들. 없으면 빈 목록을 반환한다. */
    public static List<File> pendingCopies(Context context) {
        List<File> copies = new ArrayList<>();
        String copyName = prefs(context).getString(KEY_PENDING_COPY, null);
        if (copyName != null) {
            for (String suffix : FILE_SUFFIXES) {
                File file = new File(directory(context), copyName + suffix);
                if (file.isFile()) {
                    copies.add(file);
                }
            }
        }
        return copies;
    }

    public static void markNotified(Context context) {
        prefs(context).edit().remove(KEY_PENDING_COPY).apply();
    }

    private static File directory(Context context) {
        return new File(context.getNoBackupFilesDir(), DIRECTORY);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
