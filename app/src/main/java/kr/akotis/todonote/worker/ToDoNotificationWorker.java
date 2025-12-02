package kr.akotis.todonote.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import kr.akotis.todonote.activity.MainActivity;
import kr.akotis.todonote.R;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;

public class ToDoNotificationWorker extends Worker {
    private static final String CHANNEL_ID = "todo_notification_channel";
    private static final int NOTIFICATION_ID = 1000;

    public ToDoNotificationWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        // WorkRequest에서 전달된 todoId 가져오기
        long todoId = getInputData().getLong("todo_id", -1);
        
        if (todoId == -1) {
            android.util.Log.e("ToDoNotificationWorker", "ToDo ID가 유효하지 않습니다.");
            return Result.failure();
        }

        DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
        ToDoItem todo = dbHelper.getToDoById(todoId);
        dbHelper.close();

        // ToDo가 삭제되었거나 완료된 경우 알림 표시하지 않음
        if (todo == null || todo.isCompleted() || todo.isChecked()) {
            return Result.success();
        }

        // 알림 채널 생성 (Android 8.0 이상)
        createNotificationChannel();

        // 알림 클릭 시 앱 열기
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("fragment", "todo");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                (int) todoId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 생성
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_main) // 앱 아이콘 사용
                .setContentTitle(todo.getTitle())
                .setContentText(todo.getDetail() != null && !todo.getDetail().isEmpty() 
                        ? todo.getDetail() 
                        : "할 일이 예정되어 있습니다")
                .setPriority(NotificationCompat.PRIORITY_HIGH) // 높은 우선순위로 설정
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL); // 소리, 진동, LED 등 기본 설정 사용

        // 알림 권한 확인 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    getApplicationContext(), 
                    android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return Result.success(); // 권한이 없어도 Worker는 성공으로 처리
            }
        }
        
        // 알림 표시
        NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) todoId, builder.build());
        } else {
            android.util.Log.e("ToDoNotificationWorker", "NotificationManager를 가져올 수 없습니다.");
        }

        return Result.success();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "ToDo 알림";
            String description = "할 일 알림 채널";
            // IMPORTANCE_HIGH로 설정하여 알림이 더 확실하게 표시되도록 함
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // 소리와 진동 활성화
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager notificationManager = getApplicationContext()
                    .getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}

