package kr.akotis.todonote;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        recyclerTodo.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTodo.setAdapter(todoAdapter);
        
        // 체크박스 리스너 설정 (완료 = 삭제)
        todoAdapter.setOnToDoCheckedChangeListener((todo, isChecked) -> {
            if (isChecked) {
                // 완료 시 삭제
                recyclerTodo.post(() -> {
                    int result = dbHelper.deleteToDo(todo.getId());
                    if (result > 0) {
                        loadToDos();
                        // Toast.makeText(requireContext(), "할 일이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
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

        // 날짜 클릭 리스너
        dayView.setOnClickListener(v -> {
            Calendar clickedDate = (Calendar) day.clone();
            clickedDate.set(Calendar.HOUR_OF_DAY, 0);
            clickedDate.set(Calendar.MINUTE, 0);
            clickedDate.set(Calendar.SECOND, 0);
            clickedDate.set(Calendar.MILLISECOND, 0);
            
            // 같은 날짜를 다시 클릭하면 필터 해제
            if (filteredDate != null &&
                clickedDate.get(Calendar.YEAR) == filteredDate.get(Calendar.YEAR) &&
                clickedDate.get(Calendar.MONTH) == filteredDate.get(Calendar.MONTH) &&
                clickedDate.get(Calendar.DAY_OF_MONTH) == filteredDate.get(Calendar.DAY_OF_MONTH)) {
                filteredDate = null;
            } else {
                // 다른 날짜 클릭 시 필터 변경
                filteredDate = (Calendar) clickedDate.clone();
            }
            
            selectedDate = (Calendar) day.clone();
            updateCalendar();
            filterToDosByDate();
            updateCalendarStatus();
        });

        return dayView;
    }

    private void loadToDos() {
        recyclerTodo.post(() -> {
            List<ToDoItem> todos = dbHelper.getAllToDos();
            
            // ToDo가 있는 날짜 수집
            datesWithTodos.clear();
            Calendar cal = Calendar.getInstance();
            for (ToDoItem todo : todos) {
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
        List<ToDoItem> allTodos = dbHelper.getAllToDos();
        
        if (filteredDate == null) {
            // 전체 보기
            todoAdapter.setToDos(allTodos);
        } else {
            // 선택된 날짜만 필터링
            List<ToDoItem> filteredTodos = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            for (ToDoItem todo : allTodos) {
                cal.setTimeInMillis(todo.getDate());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                
                if (cal.get(Calendar.YEAR) == filteredDate.get(Calendar.YEAR) &&
                    cal.get(Calendar.MONTH) == filteredDate.get(Calendar.MONTH) &&
                    cal.get(Calendar.DAY_OF_MONTH) == filteredDate.get(Calendar.DAY_OF_MONTH)) {
                    filteredTodos.add(todo);
                }
            }
            todoAdapter.setToDos(filteredTodos);
        }
        updateCalendarStatus();
    }
    
    private void updateCalendarStatus() {
        if (filteredDate == null) {
            tvCalendarStatus.setText("전체 일정");
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault());
            String dateStr = dateFormat.format(filteredDate.getTime());
            tvCalendarStatus.setText(dateStr + " 일정");
        }
    }

    private void showToDoInputDialog(ToDoItem todo) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_todo_input, null);

        TextInputEditText etTitle = dialogView.findViewById(R.id.et_todo_title);
        TextInputEditText etDetail = dialogView.findViewById(R.id.et_todo_detail);
        MaterialButton btnDate = dialogView.findViewById(R.id.btn_todo_date);
        MaterialButton btnTime = dialogView.findViewById(R.id.btn_todo_time);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_todo_save);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_todo_cancel);

        // 날짜와 시간 초기화
        final Calendar[] selectedDate = {Calendar.getInstance()};
        final Calendar[] selectedTime = {Calendar.getInstance()};
        
        if (todo != null) {
            editingTodo = todo;
            etTitle.setText(todo.getTitle());
            etDetail.setText(todo.getDetail());
            selectedDate[0].setTimeInMillis(todo.getDate());
            selectedTime[0].setTimeInMillis(todo.getTime());
        }
        
        updateDateButton(btnDate, selectedDate[0]);
        updateTimeButton(btnTime, selectedTime[0]);

        // 날짜 선택
        btnDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        selectedDate[0].set(year, month, dayOfMonth);
                        updateDateButton(btnDate, selectedDate[0]);
                    },
                    selectedDate[0].get(Calendar.YEAR),
                    selectedDate[0].get(Calendar.MONTH),
                    selectedDate[0].get(Calendar.DAY_OF_MONTH)
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
                // 수정 모드
                editingTodo.setTitle(title);
                editingTodo.setDetail(detail);
                editingTodo.setDate(selectedDate[0].getTimeInMillis());
                editingTodo.setTime(selectedTime[0].getTimeInMillis());
                int result = dbHelper.updateToDo(editingTodo);
                if (result > 0) {
                    loadToDos();
                    // Toast.makeText(requireContext(), "할 일이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                }
                editingTodo = null;
            } else {
                // 새 ToDo 추가
                ToDoItem newTodo = new ToDoItem(title, detail,
                        selectedDate[0].getTimeInMillis(),
                        selectedTime[0].getTimeInMillis());
                long id = dbHelper.insertToDo(newTodo);
                if (id > 0) {
                    loadToDos();
                    Toast.makeText(requireContext(), "할 일이 추가되었습니다.", Toast.LENGTH_SHORT).show();
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
                    // 왼쪽 스와이프: 완료
                    completeToDo(todo, position);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // 오른쪽 스와이프: 수정
                    editToDo(todo);
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

    private void completeToDo(ToDoItem todo, int position) {
        // 완료 처리 = 삭제
        recyclerTodo.post(() -> {
            int result = dbHelper.deleteToDo(todo.getId());
            if (result > 0) {
                loadToDos();
                // Toast.makeText(requireContext(), "할 일이 완료되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                loadToDos();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
