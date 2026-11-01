package kr.akotis.todonote.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.NoteItem;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private List<NoteItem> noteList;
    private OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(NoteItem note);
    }

    public NoteAdapter() {
        this.noteList = new ArrayList<>();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        NoteItem note = noteList.get(position);
        holder.bind(note);
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public void setNotes(List<NoteItem> notes) {
        this.noteList = notes != null ? notes : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(noteList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(noteList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<NoteItem> getNoteList() {
        return noteList;
    }

    public void setOnNoteClickListener(OnNoteClickListener listener) {
        this.listener = listener;
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent;
        private TextView tvTitle;
        private ImageView ivLock;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_note_content);
            tvTitle = itemView.findViewById(R.id.tv_note_title);
            ivLock = itemView.findViewById(R.id.iv_lock);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onNoteClick(noteList.get(position));
                    }
                }
            });
        }

        public void bind(NoteItem note) {
            // 잠금 상태 확인
            boolean isLocked = note.isLocked();
            
            if (isLocked) {
                // 잠금된 경우: 내용 숨기고 잠금 아이콘만 표시
                tvContent.setVisibility(View.GONE);
                if (ivLock != null) {
                    ivLock.setVisibility(View.VISIBLE);
                }
            } else {
                // 잠금 해제된 경우: 내용 표시하고 잠금 아이콘 숨기기
                tvContent.setVisibility(View.VISIBLE);
                String content = note.getContent();
                if (content == null || content.isEmpty()) {
                    tvContent.setText("내용 없음");
                } else {
                    tvContent.setText(content);
                }
                if (ivLock != null) {
                    ivLock.setVisibility(View.GONE);
                }
            }

            // 제목 표시 (잠금 여부와 관계없이 항상 표시)
            String title = note.getTitle();
            if (title == null || title.isEmpty()) {
                tvTitle.setText("제목 없음");
            } else {
                tvTitle.setText(title);
            }
        }
    }
}

