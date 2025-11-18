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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.TrashAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.NoteItem;

public class TrashFragment extends Fragment {

    private RecyclerView recyclerTrash;
    private DatabaseHelper dbHelper;
    private TrashAdapter trashAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trash, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        recyclerTrash = view.findViewById(R.id.recycler_trash);

        // RecyclerView 설정
        trashAdapter = new TrashAdapter();
        recyclerTrash.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTrash.setAdapter(trashAdapter);

        // 스와이프 제스처 설정
        setupSwipeGesture();

        // 휴지통 목록 로드
        loadTrash();

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
                TrashAdapter.TrashItem trashItem = trashAdapter.getTrashItemAt(position);

                if (direction == ItemTouchHelper.LEFT) {
                    // 왼쪽 스와이프: 영구 삭제 확인 다이얼로그
                    showPermanentDeleteConfirmDialog(trashItem, position);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // 오른쪽 스와이프: 복원 확인 다이얼로그
                    showRestoreConfirmDialog(trashItem, position);
                } else {
                    // 스와이프 취소 시 목록 새로고침
                    loadTrash();
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
                        // 왼쪽 스와이프 (영구 삭제) - 빨간색 배경
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
        itemTouchHelper.attachToRecyclerView(recyclerTrash);
    }

    private void showRestoreConfirmDialog(TrashAdapter.TrashItem trashItem, int position) {
        // 스와이프 취소를 위해 목록 새로고침
        loadTrash();
        
        String itemType = trashItem.getType() == TrashAdapter.TrashItem.TYPE_MEMO ? "메모" : "노트";
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("항목 복원")
                .setMessage("이 " + itemType + "를 복원하시겠습니까?")
                .setPositiveButton("복원", (dialog, which) -> {
                    restoreItem(trashItem, position);
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    // 취소 시 아무 작업도 하지 않음 (이미 loadTrash()로 복원됨)
                })
                .setOnCancelListener(dialog -> {
                    // 다이얼로그가 취소되면 아무 작업도 하지 않음
                })
                .show();
    }

    private void showPermanentDeleteConfirmDialog(TrashAdapter.TrashItem trashItem, int position) {
        // 스와이프 취소를 위해 목록 새로고침
        loadTrash();
        
        String itemType = trashItem.getType() == TrashAdapter.TrashItem.TYPE_MEMO ? "메모" : "노트";
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("영구 삭제")
                .setMessage("이 " + itemType + "를 영구적으로 삭제하시겠습니까? 복원할 수 없습니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    permanentlyDeleteItem(trashItem, position);
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    // 취소 시 아무 작업도 하지 않음 (이미 loadTrash()로 복원됨)
                })
                .setOnCancelListener(dialog -> {
                    // 다이얼로그가 취소되면 아무 작업도 하지 않음
                })
                .show();
    }

    private void restoreItem(TrashAdapter.TrashItem trashItem, int position) {
        int result = 0;
        String itemType = "";
        
        if (trashItem.getType() == TrashAdapter.TrashItem.TYPE_MEMO) {
            result = dbHelper.restoreMemo(trashItem.getId());
            itemType = "메모";
        } else if (trashItem.getType() == TrashAdapter.TrashItem.TYPE_NOTE) {
            result = dbHelper.restoreNote(trashItem.getId());
            itemType = "노트";
        }
        
        if (result > 0) {
            Toast.makeText(requireContext(), itemType + "가 복원되었습니다.", Toast.LENGTH_SHORT).show();
            loadTrash();
        } else {
            Toast.makeText(requireContext(), "복원에 실패했습니다.", Toast.LENGTH_SHORT).show();
            loadTrash();
        }
    }

    private void permanentlyDeleteItem(TrashAdapter.TrashItem trashItem, int position) {
        int result = 0;
        String itemType = "";
        
        if (trashItem.getType() == TrashAdapter.TrashItem.TYPE_MEMO) {
            result = dbHelper.permanentlyDeleteMemo(trashItem.getId());
            itemType = "메모";
        } else if (trashItem.getType() == TrashAdapter.TrashItem.TYPE_NOTE) {
            result = dbHelper.permanentlyDeleteNote(trashItem.getId());
            itemType = "노트";
        }
        
        if (result > 0) {
            Toast.makeText(requireContext(), itemType + "가 영구 삭제되었습니다.", Toast.LENGTH_SHORT).show();
            loadTrash();
        } else {
            Toast.makeText(requireContext(), "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
            loadTrash();
        }
    }

    private void loadTrash() {
        recyclerTrash.post(() -> {
            List<TrashAdapter.TrashItem> trashItems = new ArrayList<>();
            
            // 삭제된 메모 가져오기
            List<MemoItem> deletedMemos = dbHelper.getDeletedMemos();
            for (MemoItem memo : deletedMemos) {
                trashItems.add(new TrashAdapter.TrashItem(TrashAdapter.TrashItem.TYPE_MEMO, memo.getId(), memo));
            }
            
            // 삭제된 노트 가져오기
            List<NoteItem> deletedNotes = dbHelper.getDeletedNotes();
            for (NoteItem note : deletedNotes) {
                trashItems.add(new TrashAdapter.TrashItem(TrashAdapter.TrashItem.TYPE_NOTE, note.getId(), note));
            }
            
            // 삭제된 날짜 기준으로 최신 삭제가 위에 오도록 정렬 (내림차순)
            Collections.sort(trashItems, new Comparator<TrashAdapter.TrashItem>() {
                @Override
                public int compare(TrashAdapter.TrashItem o1, TrashAdapter.TrashItem o2) {
                    long deletedAt1 = 0;
                    long deletedAt2 = 0;
                    
                    if (o1.getType() == TrashAdapter.TrashItem.TYPE_MEMO) {
                        MemoItem memo = (MemoItem) o1.getItem();
                        deletedAt1 = memo.getDeletedAt();
                    } else if (o1.getType() == TrashAdapter.TrashItem.TYPE_NOTE) {
                        NoteItem note = (NoteItem) o1.getItem();
                        deletedAt1 = note.getDeletedAt();
                    }
                    
                    if (o2.getType() == TrashAdapter.TrashItem.TYPE_MEMO) {
                        MemoItem memo = (MemoItem) o2.getItem();
                        deletedAt2 = memo.getDeletedAt();
                    } else if (o2.getType() == TrashAdapter.TrashItem.TYPE_NOTE) {
                        NoteItem note = (NoteItem) o2.getItem();
                        deletedAt2 = note.getDeletedAt();
                    }
                    
                    // 내림차순 정렬 (최신 삭제가 위에)
                    return Long.compare(deletedAt2, deletedAt1);
                }
            });
            
            trashAdapter.setTrashItems(trashItems);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTrash();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

