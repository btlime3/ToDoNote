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
import android.widget.LinearLayout;
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
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
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
    private Calendar filteredDate; // 필터링된 날짜 (null이면 전체 보기)
    private Set<Long> datesWithTodos;
    private Set<String> koreanHolidays; // 한국 공휴일 (MM-dd 형식)

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
        filteredDate = null; // 전체 보기
        datesWithTodos = new HashSet<>();
        koreanHolidays = new HashSet<>();
        initKoreanHolidays();

        // RecyclerView 설정
        todoAdapter = new ToDoAdapter();
        todoAdapter.setDatabaseHelper(dbHelper);
        recyclerTodo.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTodo.setAdapter(todoAdapter);

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

    private void initKoreanHolidays() {
        // 한국 공휴일 (고정 공휴일 - MM-dd 형식)
        koreanHolidays.add("01-01"); // 신정
        koreanHolidays.add("03-01"); // 삼일절
        koreanHolidays.add("05-05"); // 어린이날
        koreanHolidays.add("06-06"); // 현충일
        koreanHolidays.add("08-15"); // 광복절
        koreanHolidays.add("10-03"); // 개천절
        koreanHolidays.add("10-09"); // 한글날
        koreanHolidays.add("12-25"); // 성탄절
        
        // 2025년 음력 공휴일 (대체 공휴일 포함)
        koreanHolidays.add("01-28"); // 설날 전날
        koreanHolidays.add("01-29"); // 설날
        koreanHolidays.add("01-30"); // 설날 다음날
        koreanHolidays.add("05-05"); // 어린이날 (부처님오신날과 같은 날)
        koreanHolidays.add("05-06"); // 대체공휴일
        koreanHolidays.add("10-05"); // 추석 전날
        koreanHolidays.add("10-06"); // 추석
        koreanHolidays.add("10-07"); // 추석 다음날
        koreanHolidays.add("10-08"); // 대체공휴일
    }
    
    private boolean isKoreanHoliday(Calendar day) {
        String dateKey = String.format(Locale.getDefault(), "%02d-%02d", 
                day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        return koreanHolidays.contains(dateKey);
    }

    private void setupCalendar() {
        // 요일 헤더 설정
        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        int[] weekdayColors = {
            getResources().getColor(R.color.calendar_sunday, null),    // 일요일 - 빨간색
            0xFF444444, // 월요일
            0xFF444444, // 화요일
            0xFF444444, // 수요일
            0xFF444444, // 목요일
            0xFF444444, // 금요일
            getResources().getColor(R.color.calendar_saturday, null)   // 토요일 - 파란색
        };
        
        for (int i = 0; i < weekdays.length; i++) {
            TextView tv = new TextView(requireContext());
            tv.setText(weekdays[i]);
            tv.setTextSize(12);
            tv.setTextColor(weekdayColors[i]);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
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
        int dayOfWeek = day.get(Calendar.DAY_OF_WEEK);
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
        
        boolean isHoliday = isKoreanHoliday(day);
        boolean isSunday = dayOfWeek == Calendar.SUNDAY;
        boolean isSaturday = dayOfWeek == Calendar.SATURDAY;

        // 기본 텍스트 색상 결정 (요일/공휴일 기준)
        int textColor;
        if (isHoliday || isSunday) {
            textColor = getResources().getColor(R.color.calendar_sunday, null); // 빨간색
        } else if (isSaturday) {
            textColor = getResources().getColor(R.color.calendar_saturday, null); // 파란색
        } else {
            textColor = 0xFF444444; // 기본 색상
        }

        // 원형 배경 크기 (일관된 크기 유지)
        int circleSize = (int) (48 * getResources().getDisplayMetrics().density); // 48dp
        
        // 스타일 적용 (우선순위: 오늘 > 선택됨 > ToDo 있음 > 기본)
        android.graphics.drawable.GradientDrawable bg = null;

        if (isToday) {
            // 오늘 날짜 - 원형 배경으로 강조
            bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(getResources().getColor(R.color.calendar_today_bg, null));
            dayView.setTextColor(getResources().getColor(R.color.calendar_today_text, null));
            dayView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else if (isSelected) {
            // 선택된 날짜 - 연한 배경
            bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(getResources().getColor(R.color.calendar_selected, null));
            dayView.setTextColor(textColor);
            dayView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else if (hasTodos) {
            // ToDo가 있는 날짜 - 연한 원형 배경 표시
            bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(getResources().getColor(R.color.calendar_has_todo_bg, null));
            dayView.setTextColor(textColor);
            dayView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            // 기본 상태 - 배경 없음
            dayView.setBackgroundColor(0x00000000);
            dayView.setTextColor(textColor);
        }

        // 원형 배경 적용
        if (bg != null) {
            bg.setSize(circleSize, circleSize);
            dayView.setBackground(bg);
        }

        // 클릭 이벤트 - 해당 날짜의 ToDo 목록 필터링
        final Calendar clickedDate = (Calendar) dateKey.clone();
        dayView.setOnClickListener(v -> {
            if (filteredDate != null && 
                filteredDate.get(Calendar.YEAR) == clickedDate.get(Calendar.YEAR) &&
                filteredDate.get(Calendar.MONTH) == clickedDate.get(Calendar.MONTH) &&
                filteredDate.get(Calendar.DAY_OF_MONTH) == clickedDate.get(Calendar.DAY_OF_MONTH)) {
                // 같은 날짜 다시 클릭 시 전체 보기로 전환
                filteredDate = null;
            } else {
                // 새 날짜 선택
                filteredDate = clickedDate;
            }
            updateCalendar();
            filterToDosByDate();
        });
        dayView.setClickable(true);

        return dayView;
    }

    private void loadToDos() {
        recyclerTodo.post(() -> {
            if (!isAdded()) {
                return;
            }
            // 모든 ToDo 가져오기 (완료된 항목 포함 - 달력 표시용)
            List<ToDoItem> allTodos = dbHelper.getAllToDos();
            
            // ToDo가 있는 날짜 수집 (완료된 항목 포함)
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
        List<ToDoItem> todosToShow;
        
        if (filteredDate != null) {
            // 선택된 날짜의 ToDo만 표시 (완료된 항목 포함)
            List<ToDoItem> allTodos = dbHelper.getAllToDos();
            todosToShow = new ArrayList<>();
            
            Calendar filterCal = (Calendar) filteredDate.clone();
            filterCal.set(Calendar.HOUR_OF_DAY, 0);
            filterCal.set(Calendar.MINUTE, 0);
            filterCal.set(Calendar.SECOND, 0);
            filterCal.set(Calendar.MILLISECOND, 0);
            long filterDateKey = filterCal.getTimeInMillis();
            
            Calendar todoCal = Calendar.getInstance();
            for (ToDoItem todo : allTodos) {
                todoCal.setTimeInMillis(todo.getDate());
                todoCal.set(Calendar.HOUR_OF_DAY, 0);
                todoCal.set(Calendar.MINUTE, 0);
                todoCal.set(Calendar.SECOND, 0);
                todoCal.set(Calendar.MILLISECOND, 0);
                
                if (todoCal.getTimeInMillis() == filterDateKey) {
                    todosToShow.add(todo);
                }
            }
        } else {
            // 전체 보기 (완료되지 않은 항목만)
            todosToShow = dbHelper.getActiveToDos();
        }
        
        todoAdapter.setToDos(todosToShow);
        updateCalendarStatus();
    }
    
    private void updateCalendarStatus() {
        if (filteredDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault());
            tvCalendarStatus.setText(dateFormat.format(filteredDate.getTime()) + " 일정");
            tvCalendarStatus.setVisibility(View.VISIBLE);
        } else {
            tvCalendarStatus.setVisibility(View.GONE);
        }
    }

    private void showToDoInputDialog(ToDoItem todo) {
        // 이전 수정 창을 닫기만 했더라도 새 할 일이 그 항목을 덮어쓰지 않게 매번 다시 정한다.
        editingTodo = todo;
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_todo_input, null);

        TextInputEditText etTitle = dialogView.findViewById(R.id.et_todo_title);
        TextInputEditText etDetail = dialogView.findViewById(R.id.et_todo_detail);
        LinearLayout btnDate = dialogView.findViewById(R.id.btn_todo_date);
        LinearLayout btnTime = dialogView.findViewById(R.id.btn_todo_time);
        TextView tvDate = dialogView.findViewById(R.id.tv_todo_date);
        TextView tvTime = dialogView.findViewById(R.id.tv_todo_time);
        AutoCompleteTextView spinnerNotification = dialogView.findViewById(R.id.spinner_notification);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_todo_save);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_todo_cancel);

        // 알림 옵션 설정 (단순화)
        String[] notificationOptions = {
            "알림없음",
            "10분전",
            "30분전",
            "1시간전",
            "2시간전",
            "5시간전"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, notificationOptions);
        spinnerNotification.setAdapter(adapter);
        spinnerNotification.setThreshold(0); // 첫 글자 입력 없이도 드롭다운 표시
        spinnerNotification.setText("알림없음", false); // 기본값

        // 날짜와 시간 초기화
        final Calendar[] selectedDate = {Calendar.getInstance()};
        final Calendar[] selectedTime = {Calendar.getInstance()};
        final String[] selectedNotificationType = {null}; // 기본값: 알림 없음
        
        if (todo != null) {
            etTitle.setText(todo.getTitle());
            etDetail.setText(todo.getDetail());
            selectedDate[0].setTimeInMillis(todo.getDate());
            selectedTime[0].setTimeInMillis(todo.getTime());
            
            // 기존 알림 타입 설정
            if (todo.getNotificationType() != null) {
                String notificationType = todo.getNotificationType();
                String displayText = getNotificationDisplayText(notificationType);
                spinnerNotification.setText(displayText, false);
                selectedNotificationType[0] = notificationType;
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
        
        updateDateText(tvDate, selectedDate[0]);
        updateTimeText(tvTime, selectedTime[0]);

        // 날짜 선택
        btnDate.setOnClickListener(v -> {
            // 현재 선택된 날짜로 달력을 열기 위해 Calendar 복사
            Calendar dateForPicker = (Calendar) selectedDate[0].clone();
            
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        // 선택한 날짜로 설정
                        selectedDate[0].set(year, month, dayOfMonth);
                        updateDateText(tvDate, selectedDate[0]);
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
                        updateTimeText(tvTime, selectedTime[0]);
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
                }
                editingTodo = null;
            } else {
                // 새 ToDo 추가
                ToDoItem newTodo = new ToDoItem(title, detail,
                        selectedDate[0].getTimeInMillis(),
                        selectedTime[0].getTimeInMillis());
                newTodo.setNotificationType(selectedNotificationType[0]);
                
                // 먼저 DB에 저장하여 ID를 얻음
                long id = dbHelper.insertToDo(newTodo);
                if (id > 0) {
                    newTodo.setId(id);
                    
                    // ID가 설정된 후 알림 예약
                    String workRequestId = scheduleNotification(newTodo);
                    newTodo.setWorkRequestId(workRequestId);
                    
                    // workRequestId를 업데이트
                    dbHelper.updateToDo(newTodo);
                    
                    loadToDos();
                    updateWidget();
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

    private void updateDateText(TextView textView, Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault());
        textView.setText(dateFormat.format(calendar.getTime()));
    }

    private void updateTimeText(TextView textView, Calendar calendar) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        textView.setText(timeFormat.format(calendar.getTime()));
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
                    showCompleteConfirmDialog(todo);
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

    private void showCompleteConfirmDialog(ToDoItem todo) {
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
                .setNegativeButton("취소", null)
                .show();
    }

    private void updateWidget() {
        if (getContext() != null) {
            try {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
                ComponentName componentName = new ComponentName(getContext(), ToDoWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                if (appWidgetIds.length > 0) {
                    // 모든 위젯 인스턴스 업데이트
                    for (int appWidgetId : appWidgetIds) {
                        ToDoWidgetProvider.updateAppWidget(getContext(), appWidgetManager, appWidgetId);
                    }
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_list);
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

        // Constraints 설정: 배터리 상태와 관계없이 실행되도록 설정
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // 네트워크 불필요
                .setRequiresBatteryNotLow(false) // 배터리 부족해도 실행
                .setRequiresCharging(false) // 충전 중이 아니어도 실행
                .setRequiresDeviceIdle(false) // 기기 유휴 상태가 아니어도 실행
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ToDoNotificationWorker.class)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .setConstraints(constraints) // Constraints 추가
                .addTag(notificationTag) // ToDo별로 고유한 태그 사용
                .build();

        // WorkManager 인스턴스 가져오기
        WorkManager workManager = WorkManager.getInstance(requireContext());
        
        // 기존 작업이 있으면 교체 (REPLACE 정책)
        // 같은 ToDo에서 같은 시간 알림이 중복 예약되는 것을 방지하면서도 새 알림이 확실히 스케줄되도록 함
        try {
            workManager.enqueueUniqueWork(notificationTag, 
                    ExistingWorkPolicy.REPLACE, 
                    workRequest);
            
            // 실패한 경우만 로그 출력
            workManager.getWorkInfoByIdLiveData(workRequest.getId())
                    .observe(getViewLifecycleOwner(), workInfo -> {
                        if (workInfo != null && workInfo.getState() == androidx.work.WorkInfo.State.FAILED) {
                            android.util.Log.e("ToDoFragment", "WorkRequest가 실패했습니다: " + workInfo.getOutputData());
                        }
                    });
        } catch (Exception e) {
            android.util.Log.e("ToDoFragment", "알림 예약 중 오류 발생", e);
            return null;
        }
        
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

        // ToDo 시간 기준으로 빼기
        int minutesToSubtract = 0;
        int hoursToSubtract = 0;

        if (notificationType.equals("10min")) {
            minutesToSubtract = 10;
        } else if (notificationType.equals("30min")) {
            minutesToSubtract = 30;
        } else if (notificationType.equals("1hour")) {
            hoursToSubtract = 1;
        } else if (notificationType.equals("2hour")) {
            hoursToSubtract = 2;
        } else if (notificationType.equals("5hour")) {
            hoursToSubtract = 5;
        }

        notificationTime.add(Calendar.HOUR_OF_DAY, -hoursToSubtract);
        notificationTime.add(Calendar.MINUTE, -minutesToSubtract);
        notificationTime.set(Calendar.SECOND, 0);
        notificationTime.set(Calendar.MILLISECOND, 0);

        return notificationTime.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * 알림 타입을 표시 텍스트로 변환
     */
    private String getNotificationDisplayText(String notificationType) {
        switch (notificationType) {
            case "10min": return "10분전";
            case "30min": return "30분전";
            case "1hour": return "1시간전";
            case "2hour": return "2시간전";
            case "5hour": return "5시간전";
            default: return "알림없음"; // 기존 데이터 호환성
        }
    }

    /**
     * 표시 텍스트를 알림 타입으로 변환
     */
    private String getNotificationTypeFromDisplay(String displayText) {
        switch (displayText) {
            case "알림없음": return null;
            case "10분전": return "10min";
            case "30분전": return "30min";
            case "1시간전": return "1hour";
            case "2시간전": return "2hour";
            case "5시간전": return "5hour";
            default: return null; // 기본값: 알림 없음
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

