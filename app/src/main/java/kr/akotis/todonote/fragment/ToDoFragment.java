package kr.akotis.todonote.fragment;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.ToDoAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;
import kr.akotis.todonote.widget.ToDoWidgetProvider;
import kr.akotis.todonote.worker.ToDoNotificationWorker;

public class ToDoFragment extends Fragment {

    private GridLayout gridCalendar;
    private GridLayout gridWeekdays;
    private TextView tvMonthYear;
    private TextView tvCalendarStatus;
    private MaterialButton btnPrevMonth;
    private MaterialButton btnNextMonth;
    private RecyclerView recyclerTodo;
    private FloatingActionButton fabTodo;
    private DatabaseHelper dbHelper;
    private ToDoAdapter todoAdapter;
    private ToDoItem editingTodo;
    
    private Calendar currentMonth;
    private Calendar selectedDate;
    private Calendar filteredDate; // 필터링된 날짜 (null이면 전체 보기)
    private Set<Long> datesWithTodos;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todo, container, false);

        dbHelper = new DatabaseHelper(requireContext());

        gridCalendar = view.findViewById(R.id.grid_calendar);
        gridWeekdays = view.findViewById(R.id.grid_weekdays);
        tvMonthYear = view.findViewById(R.id.tv_month_year);
        tvCalendarStatus = view.findViewById(R.id.tv_calendar_status);
        btnPrevMonth = view.findViewById(R.id.btn_prev_month);
        btnNextMonth = view.findViewById(R.id.btn_next_month);
        recyclerTodo = view.findViewById(R.id.recycler_todo);
        fabTodo = view.findViewById(R.id.fab_todo);

        currentMonth = Calendar.getInstance();
        selectedDate = Calendar.getInstance();
        filteredDate = null; // 전체 보기
        datesWithTodos = new HashSet<>();

        // RecyclerView 설정
        todoAdapter = new ToDoAdapter();
        todoAdapter.setDatabaseHelper(dbHelper);
        recyclerTodo.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTodo.setAdapter(todoAdapter);
        
        // 체크박스 리스너 설정 (체크 시 알림 취소)
        todoAdapter.setOnToDoCheckedChangeListener((todo, isChecked) -> {
            if (isChecked && todo.getWorkRequestId() != null) {
                // 체크 표시 시 알림 취소
                cancelNotification(todo.getWorkRequestId());
                todo.setWorkRequestId(null);
                dbHelper.updateToDo(todo);
            }
        });

        // 스와이프 제스처 설정
        setupSwipeGesture();

        // 달력 설정
        setupCalendar();

        // ToDo 목록 로드
        loadToDos();

        fabTodo.setOnClickListener(v -> showToDoInputDialog(null));

        btnPrevMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateCalendar();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateCalendar();
        });

        return view;
    }

    private void setupCalendar() {
        // 요일 헤더 설정
        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        for (String weekday : weekdays) {
            TextView tv = new TextView(requireContext());
            tv.setText(weekday);
            tv.setTextSize(12);
            tv.setTextColor(0xFF444444); // color_text_dark
            tv.setGravity(android.view.Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            tv.setLayoutParams(params);
            gridWeekdays.addView(tv);
        }

        updateCalendar();
    }

    private void updateCalendar() {
        // 월/년 표시 업데이트
        SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy년 MM월", Locale.getDefault());
        tvMonthYear.setText(monthFormat.format(currentMonth.getTime()));

        // 달력 그리드 초기화
        gridCalendar.removeAllViews();

        // 현재 월의 첫 날과 마지막 날
        Calendar firstDay = (Calendar) currentMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK) - 1; // 일요일 = 0

        Calendar lastDay = (Calendar) currentMonth.clone();
        lastDay.set(Calendar.DAY_OF_MONTH, lastDay.getActualMaximum(Calendar.DAY_OF_MONTH));

        // 오늘 날짜
        Calendar today = Calendar.getInstance();

        // 빈 칸 추가 (첫 날 이전)
        for (int i = 0; i < firstDayOfWeek; i++) {
            TextView empty = new TextView(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            empty.setLayoutParams(params);
            gridCalendar.addView(empty);
        }

        // 날짜 추가
        Calendar day = (Calendar) firstDay.clone();
        while (day.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)) {
            TextView dayView = createDayView(day, today);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            dayView.setLayoutParams(params);
            gridCalendar.addView(dayView);

            day.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private TextView createDayView(Calendar day, Calendar today) {
        TextView dayView = new TextView(requireContext());
        int dayOfMonth = day.get(Calendar.DAY_OF_MONTH);
        dayView.setText(String.valueOf(dayOfMonth));
        dayView.setTextSize(16);
        dayView.setGravity(android.view.Gravity.CENTER);
        dayView.setPadding(12, 20, 12, 20);
        dayView.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));

        // 날짜 키 생성 (년월일만)
        Calendar dateKey = (Calendar) day.clone();
        dateKey.set(Calendar.HOUR_OF_DAY, 0);
        dateKey.set(Calendar.MINUTE, 0);
        dateKey.set(Calendar.SECOND, 0);
        dateKey.set(Calendar.MILLISECOND, 0);
        long dateKeyLong = dateKey.getTimeInMillis();

        boolean isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            day.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
            day.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);
        
        boolean hasTodos = datesWithTodos.contains(dateKeyLong);
        boolean isSelected = filteredDate != null &&
            day.get(Calendar.YEAR) == filteredDate.get(Calendar.YEAR) &&
            day.get(Calendar.MONTH) == filteredDate.get(Calendar.MONTH) &&
            day.get(Calendar.DAY_OF_MONTH) == filteredDate.get(Calendar.DAY_OF_MONTH);

        // 오늘 날짜 스타일 (가장 강조) - 메인 컬러 사용
        if (isToday) {
            dayView.setBackgroundColor(0xFFA0D9C8); // color_primary
            dayView.setTextColor(0xFFFFFFFF);
            dayView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else if (isSelected) {
            // 선택된 날짜 (필터링 중) - 메인 컬러 연한 버전
            dayView.setBackgroundColor(0xFFC8E8DC); // color_primary_light
            dayView.setTextColor(0xFF444444); // color_text_dark
            dayView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else if (hasTodos) {
            // ToDo가 있는 날짜 - 보조 컬러 사용
            dayView.setBackgroundColor(0xFFF0D5C9); // color_secondary
            dayView.setTextColor(0xFF444444); // color_text_dark
            dayView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            dayView.setBackgroundColor(0x00000000);
            dayView.setTextColor(0xFF444444); // color_text_dark
        }

        // 달력 클릭 비활성화
        dayView.setOnClickListener(null);
        dayView.setClickable(false);

        return dayView;
    }

    private void loadToDos() {
        recyclerTodo.post(() -> {
            // 완료되지 않은 항목만 가져오기
            List<ToDoItem> activeTodos = dbHelper.getActiveToDos();
            
            // ToDo가 있는 날짜 수집 (모든 ToDo 포함 - 달력 표시용)
            List<ToDoItem> allTodos = dbHelper.getAllToDos();
            datesWithTodos.clear();
            Calendar cal = Calendar.getInstance();
            for (ToDoItem todo : allTodos) {
                cal.setTimeInMillis(todo.getDate());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                datesWithTodos.add(cal.getTimeInMillis());
            }
            
            updateCalendar();
            filterToDosByDate();
            updateCalendarStatus();
        });
    }
    
    private void filterToDosByDate() {
        // 달력 클릭 비활성화로 인해 항상 전체 보기 (완료되지 않은 항목만)
        List<ToDoItem> activeTodos = dbHelper.getActiveToDos();
        todoAdapter.setToDos(activeTodos);
        updateCalendarStatus();
    }
    
    private void updateCalendarStatus() {
        // 전체 일정 텍스트 제거
        tvCalendarStatus.setVisibility(View.GONE);
    }

    private void showToDoInputDialog(ToDoItem todo) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_todo_input, null);

        TextInputEditText etTitle = dialogView.findViewById(R.id.et_todo_title);
        TextInputEditText etDetail = dialogView.findViewById(R.id.et_todo_detail);
        MaterialButton btnDate = dialogView.findViewById(R.id.btn_todo_date);
        MaterialButton btnTime = dialogView.findViewById(R.id.btn_todo_time);
        AutoCompleteTextView spinnerNotification = dialogView.findViewById(R.id.spinner_notification);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_todo_save);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_todo_cancel);

        // 알림 옵션 설정
        String[] notificationOptions = {
            "아침 + 10분전",
            "아침 + 30분전",
            "아침 + 1시간전",
            "아침 + 2시간전",
            "아침 + 3시간전",
            "10분전",
            "30분전",
            "1시간전",
            "2시간전",
            "3시간전"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, notificationOptions);
        spinnerNotification.setAdapter(adapter);
        spinnerNotification.setThreshold(0); // 첫 글자 입력 없이도 드롭다운 표시
        spinnerNotification.setText("아침 + 10분전", false); // 기본값

        // 날짜와 시간 초기화
        final Calendar[] selectedDate = {Calendar.getInstance()};
        final Calendar[] selectedTime = {Calendar.getInstance()};
        final String[] selectedNotificationType = {"morning_10min"}; // 기본값
        
        if (todo != null) {
            editingTodo = todo;
            etTitle.setText(todo.getTitle());
            etDetail.setText(todo.getDetail());
            selectedDate[0].setTimeInMillis(todo.getDate());
            selectedTime[0].setTimeInMillis(todo.getTime());
            
            // 기존 알림 타입 설정
            if (todo.getNotificationType() != null) {
                String notificationType = todo.getNotificationType();
                String displayText = getNotificationDisplayText(notificationType);
                if (displayText != null) {
                    spinnerNotification.setText(displayText, false);
                    selectedNotificationType[0] = notificationType;
                }
            }
        }
        
        // 알림 선택 리스너
        spinnerNotification.setOnItemClickListener((parent, view, position, id) -> {
            String selected = notificationOptions[position];
            selectedNotificationType[0] = getNotificationTypeFromDisplay(selected);
        });
        
        // 클릭 시 드롭다운 열기
        spinnerNotification.setOnClickListener(v -> {
            spinnerNotification.showDropDown();
        });
        
        // 포커스 시 드롭다운 열기
        spinnerNotification.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                spinnerNotification.showDropDown();
            }
        });
        
        updateDateButton(btnDate, selectedDate[0]);
        updateTimeButton(btnTime, selectedTime[0]);

        // 날짜 선택
        btnDate.setOnClickListener(v -> {
            // 현재 선택된 날짜의 요일을 유지하기 위해 Calendar 복사
            Calendar dateForPicker = (Calendar) selectedDate[0].clone();
            
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        // 선택한 날짜로 설정 (요일 유지)
                        selectedDate[0].set(year, month, dayOfMonth);
                        updateDateButton(btnDate, selectedDate[0]);
                    },
                    dateForPicker.get(Calendar.YEAR),
                    dateForPicker.get(Calendar.MONTH),
                    dateForPicker.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // 시간 선택
        btnTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    requireContext(),
                    (view, hourOfDay, minute) -> {
                        selectedTime[0].set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedTime[0].set(Calendar.MINUTE, minute);
                        updateTimeButton(btnTime, selectedTime[0]);
                    },
                    selectedTime[0].get(Calendar.HOUR_OF_DAY),
                    selectedTime[0].get(Calendar.MINUTE),
                    true
            );
            timePickerDialog.show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String detail = etDetail.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "할 일 제목을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (editingTodo != null) {
                // 수정 모드 - 기존 알림 취소
                if (editingTodo.getWorkRequestId() != null) {
                    cancelNotification(editingTodo.getWorkRequestId());
                }
                
                editingTodo.setTitle(title);
                editingTodo.setDetail(detail);
                editingTodo.setDate(selectedDate[0].getTimeInMillis());
                editingTodo.setTime(selectedTime[0].getTimeInMillis());
                editingTodo.setNotificationType(selectedNotificationType[0]);
                
                // 알림 예약
                String workRequestId = scheduleNotification(editingTodo);
                editingTodo.setWorkRequestId(workRequestId);
                
                int result = dbHelper.updateToDo(editingTodo);
                if (result > 0) {
                    loadToDos();
                    updateWidget();
                    // Toast.makeText(requireContext(), "할 일이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                }
                editingTodo = null;
            } else {
                // 새 ToDo 추가
                ToDoItem newTodo = new ToDoItem(title, detail,
                        selectedDate[0].getTimeInMillis(),
                        selectedTime[0].getTimeInMillis());
                newTodo.setNotificationType(selectedNotificationType[0]);
                
                // 알림 예약
                String workRequestId = scheduleNotification(newTodo);
                newTodo.setWorkRequestId(workRequestId);
                
                long id = dbHelper.insertToDo(newTodo);
                if (id > 0) {
                    newTodo.setId(id);
                    loadToDos();
                    updateWidget();
                    //Toast.makeText(requireContext(), "할 일이 추가되었습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> {
            editingTodo = null;
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateDateButton(MaterialButton button, Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault());
        button.setText(dateFormat.format(calendar.getTime()));
    }

    private void updateTimeButton(MaterialButton button, Calendar calendar) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        button.setText(timeFormat.format(calendar.getTime()));
    }

    private void setupSwipeGesture() {
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                ToDoItem todo = todoAdapter.getToDoAt(position);

                if (direction == ItemTouchHelper.LEFT) {
                    // 왼쪽 스와이프: 완료 확인 다이얼로그 표시
                    showCompleteConfirmDialog(todo, position);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // 오른쪽 스와이프: 수정
                    editToDo(todo);
                } else {
                    // 스와이프 취소 시 목록 새로고침
                    loadToDos();
                }
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    android.graphics.Paint paint = new android.graphics.Paint();
                    
                    if (dX > 0) {
                        // 오른쪽 스와이프 (수정) - 메인 컬러 배경
                        paint.setColor(0xFFA0D9C8); // color_primary
                        c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX, (float) itemView.getBottom(), paint);
                        
                        // 수정 아이콘
                        android.graphics.drawable.Drawable editIcon = requireContext().getDrawable(android.R.drawable.ic_menu_edit);
                        if (editIcon != null) {
                            int iconMargin = (itemView.getHeight() - editIcon.getIntrinsicHeight()) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + editIcon.getIntrinsicHeight();
                            int iconLeft = itemView.getLeft() + iconMargin;
                            int iconRight = iconLeft + editIcon.getIntrinsicWidth();
                            editIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            editIcon.draw(c);
                        }
                    } else {
                        // 왼쪽 스와이프 (완료) - 강조 컬러 배경
                        paint.setColor(0xFFFFEB9E); // color_accent
                        c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);
                        
                        // 완료 아이콘 (체크마크)
                        android.graphics.drawable.Drawable checkIcon = requireContext().getDrawable(android.R.drawable.checkbox_on_background);
                        if (checkIcon != null) {
                            int iconMargin = (itemView.getHeight() - checkIcon.getIntrinsicHeight()) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + checkIcon.getIntrinsicHeight();
                            int iconRight = itemView.getRight() - iconMargin;
                            int iconLeft = iconRight - checkIcon.getIntrinsicWidth();
                            checkIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            checkIcon.draw(c);
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerTodo);
    }

    private void editToDo(ToDoItem todo) {
        showToDoInputDialog(todo);
        // 목록 새로고침 (스와이프로 인해 제거된 항목 복원)
        loadToDos();
    }

    private void showCompleteConfirmDialog(ToDoItem todo, int position) {
        // 스와이프 취소를 위해 목록 새로고침
        loadToDos();
        
        // 완료 확인 다이얼로그 표시
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("작업 완료")
                .setMessage("작업을 완료하시겠습니까?")
                .setPositiveButton("확인", (dialog, which) -> {
                    // 확인 시 완료 처리
                    // 기존 알림 취소
                    if (todo.getWorkRequestId() != null) {
                        cancelNotification(todo.getWorkRequestId());
                        todo.setWorkRequestId(null);
                    }
                    todo.setCompleted(true);
                    int result = dbHelper.updateToDo(todo);
                    if (result > 0) {
                        // ToDo 목록에서 제거 (완료된 항목은 표시되지 않음)
                        loadToDos();
                        updateWidget();
                    } else {
                        loadToDos();
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    // 취소 시 아무 작업도 하지 않음 (이미 loadToDos()로 복원됨)
                })
                .setOnCancelListener(dialog -> {
                    // 다이얼로그가 취소되면 아무 작업도 하지 않음
                })
                .show();
    }

    private void updateWidget() {
        if (getContext() != null) {
            try {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
                ComponentName componentName = new ComponentName(getContext(), ToDoWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                android.util.Log.d("ToDoFragment", "updateWidget called, found " + appWidgetIds.length + " widget(s)");
                if (appWidgetIds.length > 0) {
                    // 모든 위젯 인스턴스 업데이트
                    for (int appWidgetId : appWidgetIds) {
                        ToDoWidgetProvider.updateAppWidget(getContext(), appWidgetManager, appWidgetId);
                    }
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_list);
                    android.util.Log.d("ToDoFragment", "Widget update completed");
                } else {
                    android.util.Log.d("ToDoFragment", "No widgets found to update");
                }
            } catch (Exception e) {
                android.util.Log.e("ToDoFragment", "Error updating widget", e);
            }
        }
    }

    /**
     * 알림 예약
     */
    private String scheduleNotification(ToDoItem todo) {
        if (todo.getNotificationType() == null) {
            return null;
        }

        // ToDo의 날짜와 시간 계산
        Calendar todoDateTime = Calendar.getInstance();
        todoDateTime.setTimeInMillis(todo.getDate());
        Calendar todoTime = Calendar.getInstance();
        todoTime.setTimeInMillis(todo.getTime());
        todoDateTime.set(Calendar.HOUR_OF_DAY, todoTime.get(Calendar.HOUR_OF_DAY));
        todoDateTime.set(Calendar.MINUTE, todoTime.get(Calendar.MINUTE));
        todoDateTime.set(Calendar.SECOND, 0);
        todoDateTime.set(Calendar.MILLISECOND, 0);

        // 알림 시간 계산
        long delayMillis = calculateNotificationDelay(todoDateTime, todo.getNotificationType());
        
        if (delayMillis <= 0) {
            // 이미 지난 시간이면 알림 예약하지 않음
            return null;
        }

        // 알림 시간 계산 (겹치는 알림 처리용)
        Calendar notificationTime = Calendar.getInstance();
        notificationTime.add(Calendar.MILLISECOND, (int) delayMillis);
        
        // 알림 태그 생성: ToDo ID와 알림 시간을 조합하여 각 ToDo마다 고유한 태그 생성
        // 같은 ToDo에서 같은 시간 알림이 중복 예약되는 것을 방지하면서,
        // 서로 다른 ToDo는 같은 시간에도 각각 알림이 표시되도록 함
        String notificationTag = String.format(Locale.getDefault(), 
                "todo_notification_%d_%04d%02d%02d_%02d%02d",
                todo.getId(),
                notificationTime.get(Calendar.YEAR),
                notificationTime.get(Calendar.MONTH) + 1,
                notificationTime.get(Calendar.DAY_OF_MONTH),
                notificationTime.get(Calendar.HOUR_OF_DAY),
                notificationTime.get(Calendar.MINUTE));

        // WorkManager로 알림 예약
        Data inputData = new Data.Builder()
                .putLong("todo_id", todo.getId())
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ToDoNotificationWorker.class)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(notificationTag) // ToDo별로 고유한 태그 사용
                .build();

        // 같은 태그의 작업이 이미 있으면 새 작업을 무시 (KEEP 정책)
        // 같은 ToDo에서 같은 시간 알림이 중복 예약되는 것을 방지
        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(notificationTag, 
                        ExistingWorkPolicy.KEEP, 
                        workRequest);
        
        return workRequest.getId().toString();
    }

    /**
     * 알림 취소
     */
    private void cancelNotification(String workRequestId) {
        if (workRequestId == null) {
            return;
        }
        try {
            UUID uuid = UUID.fromString(workRequestId);
            WorkManager.getInstance(requireContext()).cancelWorkById(uuid);
        } catch (IllegalArgumentException e) {
            android.util.Log.e("ToDoFragment", "Invalid work request ID: " + workRequestId, e);
        }
    }

    /**
     * 알림 지연 시간 계산 (밀리초)
     */
    private long calculateNotificationDelay(Calendar todoDateTime, String notificationType) {
        Calendar now = Calendar.getInstance();
        Calendar notificationTime = (Calendar) todoDateTime.clone();

        // 알림 타입에 따라 시간 조정
        if (notificationType.startsWith("morning_")) {
            // 아침 시간 (8시 50분) 기준
            notificationTime.set(Calendar.HOUR_OF_DAY, 8);
            notificationTime.set(Calendar.MINUTE, 50);
            notificationTime.set(Calendar.SECOND, 0);
            notificationTime.set(Calendar.MILLISECOND, 0);
            
            // 알림 시간만큼 빼기
            if (notificationType.equals("morning_10min")) {
                notificationTime.add(Calendar.MINUTE, -10);
            } else if (notificationType.equals("morning_30min")) {
                notificationTime.add(Calendar.MINUTE, -30);
            } else if (notificationType.equals("morning_1hour")) {
                notificationTime.add(Calendar.HOUR_OF_DAY, -1);
            } else if (notificationType.equals("morning_2hour")) {
                notificationTime.add(Calendar.HOUR_OF_DAY, -2);
            } else if (notificationType.equals("morning_3hour")) {
                notificationTime.add(Calendar.HOUR_OF_DAY, -3);
            }
        } else {
            // ToDo 시간 기준으로 빼기
            if (notificationType.equals("10min")) {
                notificationTime.add(Calendar.MINUTE, -10);
            } else if (notificationType.equals("30min")) {
                notificationTime.add(Calendar.MINUTE, -30);
            } else if (notificationType.equals("1hour")) {
                notificationTime.add(Calendar.HOUR_OF_DAY, -1);
            } else if (notificationType.equals("2hour")) {
                notificationTime.add(Calendar.HOUR_OF_DAY, -2);
            } else if (notificationType.equals("3hour")) {
                notificationTime.add(Calendar.HOUR_OF_DAY, -3);
            }
        }

        return notificationTime.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * 알림 타입을 표시 텍스트로 변환
     */
    private String getNotificationDisplayText(String notificationType) {
        if (notificationType == null) {
            return null;
        }
        switch (notificationType) {
            case "morning_10min": return "아침 + 10분전";
            case "morning_30min": return "아침 + 30분전";
            case "morning_1hour": return "아침 + 1시간전";
            case "morning_2hour": return "아침 + 2시간전";
            case "morning_3hour": return "아침 + 3시간전";
            case "10min": return "10분전";
            case "30min": return "30분전";
            case "1hour": return "1시간전";
            case "2hour": return "2시간전";
            case "3hour": return "3시간전";
            default: return null;
        }
    }

    /**
     * 표시 텍스트를 알림 타입으로 변환
     */
    private String getNotificationTypeFromDisplay(String displayText) {
        switch (displayText) {
            case "아침 + 10분전": return "morning_10min";
            case "아침 + 30분전": return "morning_30min";
            case "아침 + 1시간전": return "morning_1hour";
            case "아침 + 2시간전": return "morning_2hour";
            case "아침 + 3시간전": return "morning_3hour";
            case "10분전": return "10min";
            case "30분전": return "30min";
            case "1시간전": return "1hour";
            case "2시간전": return "2hour";
            case "3시간전": return "3hour";
            default: return "morning_10min";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

