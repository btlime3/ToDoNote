package kr.akotis.todonote.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 오늘 화면 카드의 썸네일을 표시한다. 작게 줄여 앱 캐시 폴더에 저장하므로 같은 이미지를 다시 받지 않는다.
 * 캐시 폴더는 저장 공간이 부족하면 시스템이 비울 수 있고, 없어져도 다시 받으면 된다.
 */
public final class TodayThumbnailLoader {
    private static final String TAG = "TodayThumbnail";
    private static final String CACHE_DIR = "today_thumbnails";
    private static final int TARGET_SIZE_PX = 264;
    private static final int WEBP_QUALITY = 80;
    private static final int MAX_DOWNLOAD_BYTES = 6 * 1024 * 1024;
    private static final int MAX_ASPECT_RATIO = 6;
    private static final int MAX_CACHE_FILES = 150;
    private static final int TIMEOUT_MILLIS = 8000;
    private static final int MEMORY_CACHE_BYTES = 8 * 1024 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(MEMORY_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    /** 받지 못한 주소는 앱을 다시 시작할 때까지 시도하지 않는다. 스크롤할 때마다 요청하지 않기 위해서다. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private TodayThumbnailLoader() {
    }

    /** 주소가 없거나 받지 못하면 이미지 자리를 없앤다. 뷰가 재사용되므로 태그로 지금 표시할 주소를 확인한다. */
    public static void load(ImageView view, String url) {
        view.setImageDrawable(null);
        if (url == null || url.isEmpty() || FAILED.contains(url)) {
            view.setTag(null);
            view.setVisibility(View.GONE);
            return;
        }
        view.setTag(url);
        view.setVisibility(View.VISIBLE);
        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        File directory = new File(view.getContext().getCacheDir(), CACHE_DIR);
        EXECUTOR.execute(() -> {
            // 빠르게 스크롤하면 내려받기 전에 이미 다른 카드가 되어 있다.
            if (!url.equals(view.getTag())) {
                return;
            }
            Bitmap bitmap = loadFromCacheOrNetwork(directory, url);
            MAIN.post(() -> show(view, url, bitmap));
        });
    }

    private static void show(ImageView view, String url, Bitmap bitmap) {
        if (bitmap != null) {
            MEMORY.put(url, bitmap);
        } else {
            FAILED.add(url);
        }
        if (url.equals(view.getTag())) {
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
            } else {
                view.setVisibility(View.GONE);
            }
        }
    }

    private static Bitmap loadFromCacheOrNetwork(File directory, String url) {
        try {
            File file = new File(directory, fileNameFor(url));
            if (file.exists()) {
                Bitmap cached = BitmapFactory.decodeFile(file.getPath());
                if (cached != null) {
                    return cached;
                }
            }
            Bitmap bitmap = decodeSmall(download(url));
            if (bitmap != null) {
                saveToCache(directory, file, bitmap);
            }
            return bitmap;
        } catch (IOException | RuntimeException | NoSuchAlgorithmException e) {
            Log.w(TAG, "Could not load thumbnail: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static byte[] download(String address) throws IOException {
        // 수집기가 걸러 보내지만, 받는 쪽에서도 암호화되지 않은 주소는 쓰지 않는다.
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Only https is allowed");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Image is too large");
                }
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    /** 외부 파일이므로 크기를 먼저 확인하고, 필요한 크기로 줄여서 읽는다. */
    private static Bitmap decodeSmall(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int shortSide = Math.min(bounds.outWidth, bounds.outHeight);
        int longSide = Math.max(bounds.outWidth, bounds.outHeight);
        // 작은 아이콘과 긴 배너는 카드 썸네일로 쓸 수 없다.
        if (shortSide < TARGET_SIZE_PX / 4 || longSide > shortSide * MAX_ASPECT_RATIO) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (shortSide / (options.inSampleSize * 2) >= TARGET_SIZE_PX) {
            options.inSampleSize *= 2;
        }
        Bitmap sampled = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (sampled == null || Math.min(sampled.getWidth(), sampled.getHeight()) <= TARGET_SIZE_PX) {
            return sampled;
        }
        float scale = (float) TARGET_SIZE_PX / Math.min(sampled.getWidth(), sampled.getHeight());
        return Bitmap.createScaledBitmap(sampled,
                Math.round(sampled.getWidth() * scale), Math.round(sampled.getHeight() * scale), true);
    }

    private static void saveToCache(File directory, File file, Bitmap bitmap) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, output);
        }
        trimCache(directory);
    }

    private static void trimCache(File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length <= MAX_CACHE_FILES) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_CACHE_FILES; i++) {
            files[i].delete();
        }
    }

    private static String fileNameFor(String url) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        return new BigInteger(1, hash).toString(16) + ".webp";
    }
}
