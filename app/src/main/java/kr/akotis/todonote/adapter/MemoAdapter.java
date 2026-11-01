package kr.akotis.todonote.adapter;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        public MemoViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            tvContent = itemView.findViewById(R.id.tv_memo_content);
        }

        public void bind(MemoItem memo) {
            int highlight = ContextCompat.getColor(itemView.getContext(), R.color.color_accent);
            tvContent.setText(SearchHighlighter.highlight(memo.getContent(), query, highlight));
            card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(),
                    memo.getId() == focusedMemoId ? R.color.color_accent_light : R.color.color_surface));
            // 링크 클릭을 위해 MovementMethod 설정
            tvContent.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
}

