package kr.akotis.todonote.adapter;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.util.SearchHighlighter;
import kr.akotis.todonote.util.SearchQuery;

public class MemoAdapter extends RecyclerView.Adapter<MemoAdapter.MemoViewHolder> {

    private List<MemoItem> memoList;
    private SearchQuery query;
    private long focusedMemoId = -1;
    private OnPinClickListener pinClickListener;

    public interface OnPinClickListener {
        void onPinClick(MemoItem memo);
    }

    public MemoAdapter() {
        this.memoList = new ArrayList<>();
    }

    @NonNull
    @Override
    public MemoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_memo, parent, false);
        return new MemoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoViewHolder holder, int position) {
        MemoItem memo = memoList.get(position);
        holder.bind(memo);
    }

    @Override
    public int getItemCount() {
        return memoList.size();
    }

    public void setMemos(List<MemoItem> memos, SearchQuery query) {
        this.memoList = memos;
        this.query = query;
        notifyDataSetChanged();
    }

    public void setOnPinClickListener(OnPinClickListener listener) {
        pinClickListener = listener;
    }

    /** 검색 결과에서 고른 메모를 잠시 강조한다. -1이면 강조를 없앤다. */
    public void setFocusedMemoId(long memoId) {
        focusedMemoId = memoId;
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(memoList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(memoList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<MemoItem> getMemoList() {
        return memoList;
    }

    public void removeMemo(int position) {
        if (position >= 0 && position < memoList.size()) {
            memoList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public MemoItem getMemoAt(int position) {
        if (position >= 0 && position < memoList.size()) {
            return memoList.get(position);
        }
        return null;
    }

    class MemoViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent;
        private MaterialCardView card;
        private ImageButton btnPin;

        public MemoViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            tvContent = itemView.findViewById(R.id.tv_memo_content);
            btnPin = itemView.findViewById(R.id.btn_memo_pin);
            btnPin.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (pinClickListener != null && position != RecyclerView.NO_POSITION) {
                    pinClickListener.onPinClick(memoList.get(position));
                }
            });
        }

        public void bind(MemoItem memo) {
            int highlight = ContextCompat.getColor(itemView.getContext(), R.color.color_accent);
            tvContent.setText(SearchHighlighter.highlight(memo.getContent(), query, highlight));
            int background = R.color.color_surface;
            if (memo.getId() == focusedMemoId) {
                background = R.color.color_accent_light;
            } else if (memo.isPinned()) {
                background = R.color.color_primary_very_light;
            }
            card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), background));

            btnPin.setImageResource(memo.isPinned() ? R.drawable.ic_pin : R.drawable.ic_pin_outline);
            btnPin.setImageTintList(ContextCompat.getColorStateList(itemView.getContext(),
                    memo.isPinned() ? R.color.color_primary_text : R.color.color_text_light));
            btnPin.setContentDescription(memo.isPinned() ? "고정 해제" : "고정");
            // 링크 클릭을 위해 MovementMethod 설정
            tvContent.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
}

