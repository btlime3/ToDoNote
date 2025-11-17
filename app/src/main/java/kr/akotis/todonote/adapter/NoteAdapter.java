package kr.akotis.todonote.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
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

    public NoteItem getNoteAt(int position) {
        if (position >= 0 && position < noteList.size()) {
            return noteList.get(position);
        }
        return null;
    }

    public void setOnNoteClickListener(OnNoteClickListener listener) {
        this.listener = listener;
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent;
        private TextView tvTitle;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_note_content);
            tvTitle = itemView.findViewById(R.id.tv_note_title);

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
            // 내용 표시 (최대 6줄)
            String content = note.getContent();
            if (content == null || content.isEmpty()) {
                tvContent.setText("내용 없음");
            } else {
                tvContent.setText(content);
            }

            // 제목 표시
            String title = note.getTitle();
            if (title == null || title.isEmpty()) {
                tvTitle.setText("제목 없음");
            } else {
                tvTitle.setText(title);
            }
        }
    }
}

