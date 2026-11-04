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
import java.util.List;
import java.util.Locale;
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

    private RecyclerView recyclerTodo;
    private TextView tvTodoEmpty;
    private FloatingActionButton fabTodo;
    private DatabaseHelper dbHelper;
    private ToDoAdapter todoAdapter;
    private ToDoItem editingTodo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todo, container, false);

        dbHelper = new DatabaseHelper(requireContext());

        recyclerTodo = view.findViewById(R.id.recycler_todo);
        tvTodoEmpty = view.findViewById(R.id.tv_todo_empty);
        fabTodo = view.findViewById(R.id.fab_todo);
        view.findViewById(R.id.tv_todo_show_completed).setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new CompletedToDoFragment())
                        .addToBackStack(null)
                        .commit());

        // RecyclerView 설정
        todoAdapter = new ToDoAdapter();
        todoAdapter.setDatabaseHelper(dbHelper);
        recyclerTodo.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTodo.setAdapter(todoAdapter);

        // 스와이프 제스처 설정
        setupSwipeGesture();

        // ToDo 목록 로드
        loadToDos();

        fabTodo.setOnClickListener(v -> showToDoInputDialog(null));

        return view;
    }

    private void loadToDos() {
        recyclerTodo.post(() -> {
            if (!isAdded()) {
                return;
            }
            List<ToDoItem> todos = dbHelper.getActiveToDos();
            todoAdapter.setToDos(todos);
            tvTodoEmpty.setVisibility(todos.isEmpty() ? View.VISIBLE : View.GONE);
        });
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

