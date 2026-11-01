package kr.akotis.todonote.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.MessageItem;
import kr.akotis.todonote.model.NoteItem;

public class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.TrashViewHolder> {

    public static class TrashItem {
        public static final int TYPE_MEMO = 0;
        public static final int TYPE_NOTE = 1;
        public static final int TYPE_MESSAGE = 2;

        private int type;
        private long id;
        private Object item;

        public TrashItem(int type, long id, Object item) {
            this.type = type;
            this.id = id;
            this.item = item;
        }

        public int getType() {
            return type;
        }

        public long getId() {
            return id;
        }

        public Object getItem() {
            return item;
        }
    }

    private List<TrashItem> trashList;
    private OnTrashItemClickListener listener;

    public interface OnTrashItemClickListener {
        void onTrashItemClick(TrashItem item);
    }

    public TrashAdapter() {
        this.trashList = new ArrayList<>();
    }

    @NonNull
    @Override
    public TrashViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trash, parent, false);
        return new TrashViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrashViewHolder holder, int position) {
        TrashItem trashItem = trashList.get(position);
        holder.bind(trashItem);
    }

    @Override
    public int getItemCount() {
        return trashList.size();
    }

    public void setTrashItems(List<TrashItem> items) {
        this.trashList = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public TrashItem getTrashItemAt(int position) {
        if (position >= 0 && position < trashList.size()) {
            return trashList.get(position);
        }
        return null;
    }

    public List<TrashItem> getTrashItems() {
        return trashList;
    }

    public void removeTrashItem(int position) {
        if (position >= 0 && position < trashList.size()) {
            trashList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void setOnTrashItemClickListener(OnTrashItemClickListener listener) {
        this.listener = listener;
    }

    class TrashViewHolder extends RecyclerView.ViewHolder {
        private TextView tvItemType;
        private TextView tvMemoContent;
        private TextView tvNoteTitle;
        private TextView tvNoteContent;
        private TextView tvMessageTitle;
        private TextView tvMessageBody;
        private FrameLayout layoutLocked;
        private ImageView ivLock;
        private TextView tvDeletedDate;

        public TrashViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemType = itemView.findViewById(R.id.tv_item_type);
            tvMemoContent = itemView.findViewById(R.id.tv_memo_content);
            tvNoteTitle = itemView.findViewById(R.id.tv_note_title);
            tvNoteContent = itemView.findViewById(R.id.tv_note_content);
            tvMessageTitle = itemView.findViewById(R.id.tv_message_title);
            tvMessageBody = itemView.findViewById(R.id.tv_message_body);
            layoutLocked = itemView.findViewById(R.id.layout_locked);
            ivLock = itemView.findViewById(R.id.iv_lock);
            tvDeletedDate = itemView.findViewById(R.id.tv_deleted_date);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onTrashItemClick(trashList.get(position));
                    }
                }
            });
        }

        public void bind(TrashItem trashItem) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault());

            if (trashItem.getType() == TrashItem.TYPE_MEMO) {
                MemoItem memo = (MemoItem) trashItem.getItem();
                tvItemType.setText("메모");
                tvMemoContent.setVisibility(View.VISIBLE);
                tvNoteTitle.setVisibility(View.GONE);
                tvNoteContent.setVisibility(View.GONE);
                layoutLocked.setVisibility(View.GONE);
                // 다른 항목에 쓰이던 행을 재사용해도 이전 알림이 남지 않게 한다.
                tvMessageTitle.setVisibility(View.GONE);
                tvMessageBody.setVisibility(View.GONE);
                tvMemoContent.setText(memo.getContent());
                tvDeletedDate.setText("삭제된 날짜: " + dateFormat.format(memo.getDeletedAt()));
            } else if (trashItem.getType() == TrashItem.TYPE_NOTE) {
                NoteItem note = (NoteItem) trashItem.getItem();
                tvItemType.setText("노트");
                tvMemoContent.setVisibility(View.GONE);
                tvMessageTitle.setVisibility(View.GONE);
                tvMessageBody.setVisibility(View.GONE);
                tvNoteTitle.setVisibility(View.VISIBLE);
                tvNoteTitle.setText(note.getTitle() != null && !note.getTitle().isEmpty() ? note.getTitle() : "제목 없음");
                
                // 잠금된 노트인 경우
                if (note.isLocked()) {
                    tvNoteContent.setVisibility(View.GONE);
                    layoutLocked.setVisibility(View.VISIBLE);
                } else {
                    // 잠금되지 않은 노트인 경우
                    tvNoteContent.setVisibility(View.VISIBLE);
                    layoutLocked.setVisibility(View.GONE);
                    tvNoteContent.setText(note.getContent() != null && !note.getContent().isEmpty() ? note.getContent() : "내용 없음");
                }
                
                tvDeletedDate.setText("삭제된 날짜: " + dateFormat.format(note.getDeletedAt()));
            } else if (trashItem.getType() == TrashItem.TYPE_MESSAGE) {
                MessageItem message = (MessageItem) trashItem.getItem();
                tvItemType.setText("알림");
                tvMemoContent.setVisibility(View.GONE);
                tvNoteTitle.setVisibility(View.GONE);
                tvNoteContent.setVisibility(View.GONE);
                tvMessageTitle.setVisibility(View.VISIBLE);
                tvMessageBody.setVisibility(View.VISIBLE);
                layoutLocked.setVisibility(View.GONE);
                tvMessageTitle.setText(message.getTitle() != null && !message.getTitle().isEmpty() ? message.getTitle() : "제목 없음");
                // 알림 본문은 HTML이므로 태그가 그대로 보이지 않게 글자로 바꾼다.
                tvMessageBody.setText(message.getBody() != null && !message.getBody().isEmpty()
                        ? HtmlCompat.fromHtml(message.getBody(), HtmlCompat.FROM_HTML_MODE_LEGACY) : "내용 없음");
                tvDeletedDate.setText("삭제된 날짜: " + dateFormat.format(message.getDeletedAt()));
            }
        }
    }
}

