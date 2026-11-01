package kr.akotis.todonote.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.UUID;

import androidx.work.WorkManager;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.ToDoAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;

public class CompletedToDoFragment extends Fragment {

    private RecyclerView recyclerTodo;
    private View tvEmpty;
    private DatabaseHelper dbHelper;
    private ToDoAdapter todoAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_completed_todo, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        recyclerTodo = view.findViewById(R.id.recycler_todo);
        tvEmpty = view.findViewById(R.id.tv_completed_empty);

        // RecyclerView 설정
        todoAdapter = new ToDoAdapter();
        todoAdapter.setDatabaseHelper(dbHelper);
        recyclerTodo.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTodo.setAdapter(todoAdapter);

        // 스와이프 제스처 설정
        setupSwipeGesture();

        // ToDo 목록 로드
        loadCompletedToDos();

        return view;
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
                    // 왼쪽 스와이프: 삭제 확인 다이얼로그 표시
                    showDeleteConfirmDialog(todo);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // 오른쪽 스와이프: 복원 확인 다이얼로그 표시
                    showRestoreConfirmDialog(todo);
                } else {
                    // 스와이프 취소 시 목록 새로고침
                    loadCompletedToDos();
                }
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    android.graphics.Paint paint = new android.graphics.Paint();
                    
                    if (dX > 0) {
                        // 오른쪽 스와이프 (복원) - 초록색 배경
                        paint.setColor(0xFF4CAF50); // 초록색
                        c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX, (float) itemView.getBottom(), paint);
                        
                        // 복원 아이콘
                        android.graphics.drawable.Drawable restoreIcon = requireContext().getDrawable(android.R.drawable.ic_menu_revert);
                        if (restoreIcon != null) {
                            int iconMargin = (itemView.getHeight() - restoreIcon.getIntrinsicHeight()) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + restoreIcon.getIntrinsicHeight();
                            int iconLeft = itemView.getLeft() + iconMargin;
                            int iconRight = iconLeft + restoreIcon.getIntrinsicWidth();
                            restoreIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            restoreIcon.draw(c);
                        }
                    } else {
                        // 왼쪽 스와이프 (삭제) - 빨간색 배경
                        paint.setColor(0xFFFF6B6B); // 빨간색
                        c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);
                        
                        // 삭제 아이콘
                        android.graphics.drawable.Drawable deleteIcon = requireContext().getDrawable(android.R.drawable.ic_menu_delete);
                        if (deleteIcon != null) {
                            int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                            int iconRight = itemView.getRight() - iconMargin;
                            int iconLeft = iconRight - deleteIcon.getIntrinsicWidth();
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            deleteIcon.draw(c);
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerTodo);
    }

    private void showRestoreConfirmDialog(ToDoItem todo) {
        // 스와이프 취소를 위해 목록 새로고침
        loadCompletedToDos();
        
        // 복원 확인 다이얼로그 표시
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("작업 복원")
                .setMessage("이 작업을 ToDo 목록으로 복원하시겠습니까?")
                .setPositiveButton("복원", (dialog, which) -> {
                    // 확인 시 복원 (completed를 false로 설정)
                    todo.setCompleted(false);
                    int result = dbHelper.updateToDo(todo);
                    if (result > 0) {
                        // 목록에서 제거
                        loadCompletedToDos();
                    } else {
                        loadCompletedToDos();
                        Toast.makeText(requireContext(), "복원에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showDeleteConfirmDialog(ToDoItem todo) {
        // 스와이프 취소를 위해 목록 새로고침
        loadCompletedToDos();
        
        // 삭제 확인 다이얼로그 표시
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("작업 삭제")
                .setMessage("이 작업을 영구적으로 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    // 확인 시 영구 삭제
                    // 기존 알림 취소
                    if (todo.getWorkRequestId() != null) {
                        cancelNotification(todo.getWorkRequestId());
                    }
                    int result = dbHelper.deleteToDo(todo.getId());
                    if (result > 0) {
                        // 목록에서 제거
                        loadCompletedToDos();
                    } else {
                        loadCompletedToDos();
                        Toast.makeText(requireContext(), "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
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
            android.util.Log.e("CompletedToDoFragment", "Invalid work request ID: " + workRequestId, e);
        }
    }

    private void loadCompletedToDos() {
        recyclerTodo.post(() -> {
            if (!isAdded()) {
                return;
            }
            // 완료된 항목만 가져오기
            List<ToDoItem> completedTodos = dbHelper.getCompletedToDos();
            todoAdapter.setToDos(completedTodos);
            tvEmpty.setVisibility(completedTodos.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCompletedToDos();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

