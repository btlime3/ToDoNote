package kr.akotis.todonote.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.activity.MainActivity;
import kr.akotis.todonote.adapter.MessageAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.MessageItem;

public class MessageFragment extends Fragment {

    private RecyclerView recyclerMessages;
    private TextView tvEmptyMessage;
    private DatabaseHelper dbHelper;
    private MessageAdapter messageAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_message, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        recyclerMessages = view.findViewById(R.id.recycler_messages);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message);

        // RecyclerView 설정
        messageAdapter = new MessageAdapter();
        recyclerMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerMessages.setAdapter(messageAdapter);

        // 스와이프 삭제 설정
        setupSwipeToDelete();

        // 메시지 로드
        loadMessages();

        return view;
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, 
                                  @NonNull RecyclerView.ViewHolder viewHolder, 
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                MessageItem message = messageAdapter.getMessageAt(position);
                
                if (message != null) {
                    showDeleteConfirmDialog(message, position);
                }
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, 
                                    @NonNull RecyclerView recyclerView, 
                                    @NonNull RecyclerView.ViewHolder viewHolder, 
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    android.graphics.Paint paint = new android.graphics.Paint();
                    
                    // 왼쪽 스와이프 (삭제) - 빨간색 배경
                    paint.setColor(0xFFFF6B6B);
                    c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), 
                            (float) itemView.getRight(), (float) itemView.getBottom(), paint);
                    
                    // 삭제 아이콘
                    android.graphics.drawable.Drawable deleteIcon = 
                            requireContext().getDrawable(android.R.drawable.ic_menu_delete);
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
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(recyclerMessages);
    }

    private void showDeleteConfirmDialog(MessageItem message, int position) {
        // 먼저 목록 새로고침 (스와이프 취소)
        loadMessages();
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("알림 삭제")
                .setMessage("이 알림을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    dbHelper.deleteMessage(message.getId());
                    loadMessages();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void loadMessages() {
        try {
            List<MessageItem> messages = dbHelper.getAllMessages();
            messageAdapter.setMessages(messages);

            if (messages.isEmpty()) {
                tvEmptyMessage.setVisibility(View.VISIBLE);
                recyclerMessages.setVisibility(View.GONE);
            } else {
                tvEmptyMessage.setVisibility(View.GONE);
                recyclerMessages.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            android.util.Log.e("MessageFragment", "Error loading messages", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 페이지에 들어오면 모든 메시지를 읽음 처리
        dbHelper.markAllMessagesAsRead();
        loadMessages();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateNotificationBadge();
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

