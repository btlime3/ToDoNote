package kr.akotis.todonote;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;

public class ToDoWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ToDoRemoteViewsFactory(this.getApplicationContext());
    }

    private class ToDoRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private Context context;
        private List<ToDoItem> todoList;

        ToDoRemoteViewsFactory(Context context) {
            this.context = context;
            this.todoList = new ArrayList<>();
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        private void loadData() {
            try {
                DatabaseHelper helper = new DatabaseHelper(context);
                // 완료되지 않은 항목만 가져오기
                List<ToDoItem> loadedList = helper.getActiveToDos();
                helper.close();
                
                if (loadedList != null && !loadedList.isEmpty()) {
                    todoList = loadedList;
                } else {
                    todoList = new ArrayList<>();
                }
            } catch (Exception e) {
                todoList = new ArrayList<>();
            }
        }

        @Override
        public void onDestroy() {
            todoList = null;
        }

        @Override
        public int getCount() {
            return todoList != null ? todoList.size() : 0;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (todoList == null || position < 0 || position >= todoList.size()) {
                return createEmptyView();
            }

            ToDoItem todo = todoList.get(position);
            if (todo == null) {
                return createEmptyView();
            }

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item_todo);
            
            // 제목 설정
            String title = todo.getTitle();
            if (title == null || title.isEmpty()) {
                title = "(제목 없음)";
            }
            views.setTextViewText(R.id.widget_todo_title, title);

            // 날짜 및 상대 날짜 설정
            String dateText = formatDate(todo.getDate());
            views.setTextViewText(R.id.widget_todo_date, dateText);

            // 시간 설정
            String timeText = formatTime(todo.getTime());
            views.setTextViewText(R.id.widget_todo_time, timeText);

            // 항목 클릭 시 앱 열기 (TodoFragment로 이동)
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra("fragment", "todo");
            views.setOnClickFillInIntent(R.id.widget_todo_title, fillInIntent);
            views.setOnClickFillInIntent(R.id.widget_todo_date, fillInIntent);
            views.setOnClickFillInIntent(R.id.widget_todo_time, fillInIntent);

            return views;
        }

        private RemoteViews createEmptyView() {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item_todo);
            views.setTextViewText(R.id.widget_todo_title, "");
            views.setTextViewText(R.id.widget_todo_date, "");
            views.setTextViewText(R.id.widget_todo_time, "");
            return views;
        }

        private String formatDate(long dateMillis) {
            if (dateMillis <= 0) {
                return "";
            }

            try {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(dateMillis);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
                String dateStr = dateFormat.format(cal.getTimeInMillis());

                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);
                
                Calendar todoDate = Calendar.getInstance();
                todoDate.setTimeInMillis(dateMillis);
                todoDate.set(Calendar.HOUR_OF_DAY, 0);
                todoDate.set(Calendar.MINUTE, 0);
                todoDate.set(Calendar.SECOND, 0);
                todoDate.set(Calendar.MILLISECOND, 0);

                long diff = todoDate.getTimeInMillis() - today.getTimeInMillis();
                long days = diff / (24 * 60 * 60 * 1000);

                String relativeDate = "";
                if (days == 0) {
                    relativeDate = "오늘";
                } else if (days == 1) {
                    relativeDate = "내일";
                } else if (days > 1) {
                    relativeDate = days + "일 후";
                } else if (days == -1) {
                    relativeDate = "어제";
                } else {
                    relativeDate = Math.abs(days) + "일 전";
                }

                return dateStr + " (" + relativeDate + ")";
            } catch (Exception e) {
                return "";
            }
        }

        private String formatTime(long timeMillis) {
            if (timeMillis <= 0) {
                return "";
            }

            try {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return timeFormat.format(timeMillis);
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return todoList != null && position < todoList.size() ? todoList.get(position).getId() : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}

