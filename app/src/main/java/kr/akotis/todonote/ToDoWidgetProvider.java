package kr.akotis.todonote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

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

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
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
        
        // 위젯 리스트 항목 클릭 시 앱 열기 (TodoFragment로 이동)
        Intent listIntent = new Intent(context, MainActivity.class);
        listIntent.putExtra("fragment", "todo");
        listIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent listPendingIntent = PendingIntent.getActivity(
                context, 0, listIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setPendingIntentTemplate(R.id.widget_todo_list, listPendingIntent);

        // 데이터 변경 알림 후 위젯 업데이트
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_list);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE_TODO.equals(intent.getAction())) {
            long todoId = intent.getLongExtra(EXTRA_TODO_ID, -1);
            if (todoId != -1) {
                DatabaseHelper dbHelper = new DatabaseHelper(context);
                ToDoItem todo = dbHelper.getToDoById(todoId);
                if (todo != null) {
                    dbHelper.deleteToDo(todoId);
                    updateAllWidgets(context);
                }
                dbHelper.close();
            }
        }
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
}

