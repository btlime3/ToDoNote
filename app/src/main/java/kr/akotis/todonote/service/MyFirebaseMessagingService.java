package kr.akotis.todonote.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.text.HtmlCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import kr.akotis.todonote.R;
import kr.akotis.todonote.activity.MainActivity;
import java.util.List;

import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.database.TodayFeedDatabase;
import kr.akotis.todonote.model.MessageItem;
import kr.akotis.todonote.model.TodayItem;
import kr.akotis.todonote.util.TodayFeedParser;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static final String ACTION_MESSAGE_RECEIVED = "kr.akotis.todonote.MESSAGE_RECEIVED";
    public static final String ACTION_TODAY_UPDATED = "kr.akotis.todonote.TODAY_UPDATED";

    private static final String TYPE_TODAY = "today";

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "fcm_notification_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // 토큰을 서버에 전송하거나 저장하는 로직 (필요시 구현)
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Data payload만 확인 (서버에서 data payload만 보내도록 수정됨)
        if (remoteMessage.getData().isEmpty()) {
            return;
        }

        // 오늘 화면의 소식은 알림을 띄우지 않고 저장만 한다.
        if (TYPE_TODAY.equals(remoteMessage.getData().get("type"))) {
            saveTodayItems(remoteMessage.getData().get("items"));
            return;
        }

        String title = remoteMessage.getData().get("title");
        String body = remoteMessage.getData().get("body");

        // title이 없으면 기본값 사용
        if (title == null || title.trim().isEmpty()) {
            title = "알림";
        }

        // 1. DB에 저장 (백그라운드에서도 작동)
        saveMessageToDatabase(title, body != null ? body : "");

        // 2. 사용자가 볼 수 있게 직접 알림 띄우기 (필수!)
        showNotificationWithMessage(title, body != null ? body : "");
    }

    private void saveTodayItems(String itemsJson) {
        try {
            long now = System.currentTimeMillis();
            List<TodayItem> items = TodayFeedParser.parse(itemsJson, now);
            if (!items.isEmpty()) {
                TodayFeedDatabase.getInstance(this).save(items, now);
                sendBroadcast(new Intent(ACTION_TODAY_UPDATED).setPackage(getPackageName()));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to save today items", e);
        }
    }

    /**
     * DB에 메시지 저장 (백그라운드에서도 작동)
     */
    private void saveMessageToDatabase(String title, String body) {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
            MessageItem message = new MessageItem(title, body);
            dbHelper.insertMessage(message);
            dbHelper.close();
            sendBroadcast(new Intent(ACTION_MESSAGE_RECEIVED).setPackage(getPackageName()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to save message to database", e);
        }
    }

    /**
     * 사용자가 볼 수 있게 직접 알림 띄우기 (필수!)
     * data payload만 있는 경우 시스템이 자동으로 알림을 띄우지 않으므로 앱이 직접 띄워야 함
     */
    private void showNotificationWithMessage(String title, String body) {
        // 알림 채널 생성
        createNotificationChannel();

        // 알림 클릭 시 MainActivity로 이동 (message fragment 열기)
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("fragment", "message");
        // 실행 중인 화면을 새로 만들지 않고 onNewIntent로 전달받는다.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 고유한 requestCode 사용 (시간 기반)
        int requestCode = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 본문은 HTML이므로 태그가 그대로 보이지 않게 글자로 바꾼다.
        CharSequence bodyText = HtmlCompat.fromHtml(body != null ? body : "", HtmlCompat.FROM_HTML_MODE_LEGACY);

        // 알림 생성
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_main) // 앱 아이콘 사용
                .setContentTitle(title != null ? title : "새 알림")
                .setContentText(bodyText)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        // 긴 본문인 경우 BigTextStyle 사용
        if (bodyText.length() > 50) {
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(bodyText));
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            // 고유한 알림 ID 사용 (시간 기반)
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private void createNotificationChannel() {
        CharSequence name = "FCM 알림";
        String description = "서버에서 전송된 알림";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        channel.enableLights(true);
        channel.enableVibration(true);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}

