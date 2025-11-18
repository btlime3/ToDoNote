package kr.akotis.todonote.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.work.WorkManager;

import java.util.UUID;

import kr.akotis.todonote.R;
import kr.akotis.todonote.activity.MainActivity;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;

public class ToDoWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_TODO = "kr.akotis.todonote.ACTION_TOGGLE_TODO";
    public static final String EXTRA_TODO_ID = "todo_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_todo);

        // RemoteViewsService 설정
        Intent intent = new Intent(context, ToDoWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse("widget://" + appWidgetId));
        views.setRemoteAdapter(R.id.widget_todo_list, intent);

        // 빈 뷰 설정
        views.setEmptyView(R.id.widget_todo_list, R.id.widget_empty_view);

        // 위젯 제목 클릭 시 앱 열기 (TodoFragment로 이동)
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.putExtra("fragment", "todo");
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent);
        
        // 체크박스 클릭을 위한 템플릿 설정 (먼저 설정해야 함)
        Intent toggleTemplateIntent = new Intent(context, ToDoWidgetProvider.class);
        toggleTemplateIntent.setAction(ACTION_TOGGLE_TODO);
        // 템플릿 Intent에 data URI 패턴 설정 (FillInIntent와 병합될 때 사용)
        toggleTemplateIntent.setData(Uri.parse("widget://todo/"));
        PendingIntent toggleTemplatePendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleTemplateIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setPendingIntentTemplate(R.id.widget_todo_list, toggleTemplatePendingIntent);

        // 데이터 변경 알림 후 위젯 업데이트
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_list);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        // 커스텀 액션을 먼저 처리
        if (ACTION_TOGGLE_TODO.equals(action)) {
            // Intent의 모든 내용 로그 출력
            Log.d("ToDoWidget", "Intent action: " + intent.getAction());
            Log.d("ToDoWidget", "Intent data: " + intent.getData());
            Log.d("ToDoWidget", "Intent extras: " + intent.getExtras());
            if (intent.getExtras() != null) {
                for (String key : intent.getExtras().keySet()) {
                    Log.d("ToDoWidget", "Extra key: " + key + ", value: " + intent.getExtras().get(key));
                }
            }
            
            long todoId = intent.getLongExtra(EXTRA_TODO_ID, -1);
            
            // FillInIntent에서 extra가 전달되지 않으므로 URI에서 추출 시도
            // 템플릿과 FillInIntent가 병합될 때 data URI가 병합됨
            Uri data = intent.getData();
            if (data != null) {
                String uriString = data.toString();
                Log.d("ToDoWidget", "Parsing todoId from URI: " + uriString);
                // URI 형식: widget://todo/{todoId}
                if (uriString.contains("/todo/")) {
                    try {
                        int startIndex = uriString.indexOf("/todo/") + 6;
                        int endIndex = uriString.indexOf("/", startIndex);
                        if (endIndex == -1) {
                            endIndex = uriString.length();
                        }
                        String todoIdStr = uriString.substring(startIndex, endIndex);
                        todoId = Long.parseLong(todoIdStr);
                        Log.d("ToDoWidget", "Extracted todoId from URI: " + todoId);
                    } catch (Exception e) {
                        Log.e("ToDoWidget", "Failed to parse todoId from URI: " + uriString, e);
                    }
                }
            }
            
            Log.d("ToDoWidget", "Toggle action received, todoId: " + todoId + ", URI: " + intent.getData());
            
            if (todoId != -1) {
                DatabaseHelper dbHelper = new DatabaseHelper(context);
                ToDoItem todo = dbHelper.getToDoById(todoId);
                
                if (todo != null) {
                    // 체크 상태 토글
                    boolean currentState = todo.isChecked();
                    boolean newCheckedState = !currentState;
                    Log.d("ToDoWidget", "Toggling todo " + todoId + " from " + currentState + " to " + newCheckedState);
                    
                    todo.setChecked(newCheckedState);
                    
                    // 체크 표시 시 알림 취소
                    if (newCheckedState && todo.getWorkRequestId() != null) {
                        cancelNotification(context, todo.getWorkRequestId());
                        todo.setWorkRequestId(null);
                    }
                    
                    int result = dbHelper.updateToDo(todo);
                    dbHelper.close();
                    
                    Log.d("ToDoWidget", "Update result: " + result);
                    
                    // 위젯 업데이트
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    ComponentName componentName = new ComponentName(context, ToDoWidgetProvider.class);
                    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                    
                    Log.d("ToDoWidget", "Found " + appWidgetIds.length + " widget(s) to update");
                    
                    // 데이터 변경 알림 (먼저 호출)
                    for (int appWidgetId : appWidgetIds) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_list);
                    }
                    
                    // 위젯 업데이트
                    onUpdate(context, appWidgetManager, appWidgetIds);
                    
                    Log.d("ToDoWidget", "Widget update completed");
                } else {
                    Log.d("ToDoWidget", "Todo not found: " + todoId);
                    dbHelper.close();
                }
            } else {
                Log.d("ToDoWidget", "Invalid todoId: " + todoId);
            }
            return; // 커스텀 액션 처리 후 종료
        }
        
        // 다른 액션은 기본 처리
        super.onReceive(context, intent);
    }

    @Override
    public void onEnabled(Context context) {
        updateAllWidgets(context);
    }

    @Override
    public void onDisabled(Context context) {
        // 마지막 위젯이 제거될 때
    }

    private void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, ToDoWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }
    
    /**
     * 알림 취소
     */
    private void cancelNotification(Context context, String workRequestId) {
        if (workRequestId == null) {
            return;
        }
        try {
            UUID uuid = UUID.fromString(workRequestId);
            WorkManager.getInstance(context).cancelWorkById(uuid);
        } catch (IllegalArgumentException e) {
            Log.e("ToDoWidgetProvider", "Invalid work request ID: " + workRequestId, e);
        }
    }
}

