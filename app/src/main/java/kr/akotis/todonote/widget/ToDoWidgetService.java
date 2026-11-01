package kr.akotis.todonote.widget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.R;
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

            // 체크된 항목은 취소선 표시
            if (todo.isChecked()) {
                views.setInt(R.id.widget_todo_title, "setPaintFlags",
                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
            } else {
                views.setInt(R.id.widget_todo_title, "setPaintFlags", android.graphics.Paint.ANTI_ALIAS_FLAG);
            }

            // 날짜 및 상대 날짜 설정
            String dateText = formatDate(todo.getDate());
            views.setTextViewText(R.id.widget_todo_date, dateText);

            // 시간 설정
            String timeText = formatTime(todo.getTime());
            views.setTextViewText(R.id.widget_todo_time, timeText);

            // 체크박스 이미지 설정
            if (todo.isChecked()) {
                views.setImageViewResource(R.id.widget_todo_checkbox, android.R.drawable.checkbox_on_background);
            } else {
                views.setImageViewResource(R.id.widget_todo_checkbox, android.R.drawable.checkbox_off_background);
            }

            // 날짜별 스타일 적용
            Calendar now = Calendar.getInstance();
            Calendar todoDateTime = Calendar.getInstance();
            todoDateTime.setTimeInMillis(todo.getDate());
            Calendar todoTime = Calendar.getInstance();
            todoTime.setTimeInMillis(todo.getTime());
            todoDateTime.set(Calendar.HOUR_OF_DAY, todoTime.get(Calendar.HOUR_OF_DAY));
            todoDateTime.set(Calendar.MINUTE, todoTime.get(Calendar.MINUTE));
            todoDateTime.set(Calendar.SECOND, 0);
            todoDateTime.set(Calendar.MILLISECOND, 0);
            
            // 오늘 날짜인지 확인 (날짜만 비교)
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            
            Calendar todoDate = Calendar.getInstance();
            todoDate.setTimeInMillis(todo.getDate());
            todoDate.set(Calendar.HOUR_OF_DAY, 0);
            todoDate.set(Calendar.MINUTE, 0);
            todoDate.set(Calendar.SECOND, 0);
            todoDate.set(Calendar.MILLISECOND, 0);
            
            boolean isToday = today.getTimeInMillis() == todoDate.getTimeInMillis();
            boolean isPast = todoDateTime.getTimeInMillis() < now.getTimeInMillis();
            
            if (isToday) {
                // 오늘인 경우 하이라이트 배경 (#FFF5D1 - color_accent_light)
                views.setInt(R.id.widget_item_container, "setBackgroundColor", 0xFFFFF5D1);
                // 텍스트 색상
                views.setTextColor(R.id.widget_todo_title, 0xFF444444);
                views.setTextColor(R.id.widget_todo_date, 0xFF666666);
                views.setTextColor(R.id.widget_todo_time, 0xFF666666);
            } else if (isPast) {
                // 지난 경우 회색 배경 및 텍스트
                views.setInt(R.id.widget_item_container, "setBackgroundColor", 0xFFE0E0E0);
                views.setTextColor(R.id.widget_todo_title, 0xFF999999);
                views.setTextColor(R.id.widget_todo_date, 0xFF999999);
                views.setTextColor(R.id.widget_todo_time, 0xFF999999);
            } else {
                // 기본 색상
                views.setInt(R.id.widget_item_container, "setBackgroundColor", 0xFFFFFFFF);
                views.setTextColor(R.id.widget_todo_title, 0xFF444444);
                views.setTextColor(R.id.widget_todo_date, 0xFF666666);
                views.setTextColor(R.id.widget_todo_time, 0xFF666666);
            }

            // 체크박스 클릭 시 체크 상태 토글 (FillInIntent 사용 - 템플릿과 함께)
            // 템플릿이 FLAG_MUTABLE이고 data가 없어서 이 data URI가 템플릿에 채워진다.
            Intent toggleFillInIntent = new Intent();
            // Provider는 이 data URI에서 할 일 ID를 읽는다.
            toggleFillInIntent.setData(Uri.parse("widget://todo/" + todo.getId()));
            views.setOnClickFillInIntent(R.id.widget_todo_checkbox, toggleFillInIntent);

            // 제목, 날짜, 시간 클릭: 템플릿이 토글 브로드캐스트이므로 앱을 열지 않는다.
            // 이 FillInIntent에는 data가 없어서 Provider가 토글하지 않고 끝낸다.
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

