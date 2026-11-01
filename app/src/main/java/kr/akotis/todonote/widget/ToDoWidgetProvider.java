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

import kr.akotis.todonote.R;
import kr.akotis.todonote.activity.MainActivity;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;

public class ToDoWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_TODO = "kr.akotis.todonote.ACTION_TOGGLE_TODO";

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
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent);
        
        // 체크박스 클릭을 위한 템플릿 설정 (먼저 설정해야 함)
        Intent toggleTemplateIntent = new Intent(context, ToDoWidgetProvider.class);
        toggleTemplateIntent.setAction(ACTION_TOGGLE_TODO);
        // 항목별 data는 FillInIntent가 채운다. 템플릿에 data가 있거나 불변이면 병합되지 않는다.
        PendingIntent toggleTemplatePendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleTemplateIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
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
            long todoId = -1;

            // 체크박스의 FillInIntent는 할 일 ID를 data URI로 넘긴다.
            // 템플릿이 FLAG_MUTABLE이고 data가 없어서 이 URI가 템플릿에 채워진다.
            // 제목, 날짜, 시간의 FillInIntent에는 data가 없어서 토글하지 않는다.
            Uri data = intent.getData();
            if (data != null) {
                String uriString = data.toString();
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
                    } catch (Exception e) {
                        Log.e("ToDoWidget", "Failed to parse todoId from URI: " + uriString, e);
                    }
                }
            }
            
            if (todoId != -1) {
                DatabaseHelper dbHelper = new DatabaseHelper(context);
                ToDoItem todo = dbHelper.getToDoById(todoId);
                
                if (todo != null) {
                    // 체크 상태 토글
                    boolean currentState = todo.isChecked();
                    boolean newCheckedState = !currentState;
                    
                    todo.setChecked(newCheckedState);
                    
                    // 체크 상태와 알림은 무관 (알림 설정이 되어있다면 계속 작동)
                    
                    dbHelper.updateToDo(todo);
                    dbHelper.close();

                    // 위젯 업데이트
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    ComponentName componentName = new ComponentName(context, ToDoWidgetProvider.class);
                    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

                    // 데이터 변경 알림 (먼저 호출)
                    for (int appWidgetId : appWidgetIds) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_list);
                    }
                    
                    // 위젯 업데이트
                    onUpdate(context, appWidgetManager, appWidgetIds);
                } else {
                    dbHelper.close();
                }
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

    private void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, ToDoWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }
}

