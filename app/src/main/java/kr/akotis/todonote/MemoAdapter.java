package kr.akotis.todonote;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoAdapter extends RecyclerView.Adapter<MemoAdapter.MemoViewHolder> {

    private List<MemoItem> memoList;

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

    public void setMemos(List<MemoItem> memos) {
        this.memoList = memos;
        notifyDataSetChanged();
    }

    public void addMemo(MemoItem memo) {
        memoList.add(memo);
        notifyItemInserted(memoList.size() - 1);
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

        public MemoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_memo_content);
        }

        public void bind(MemoItem memo) {
            tvContent.setText(memo.getContent());
            // 링크 클릭을 위해 MovementMethod 설정
            tvContent.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
}

