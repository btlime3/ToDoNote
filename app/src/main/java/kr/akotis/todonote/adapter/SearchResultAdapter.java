package kr.akotis.todonote.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.R;
import kr.akotis.todonote.util.SearchHighlighter;
import kr.akotis.todonote.util.SearchQuery;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder> {

    private static final int EXCERPT_LENGTH = 120;

    public enum Type {
        MEMO("메모"), NOTE("노트"), MESSAGE("알림"), TODO("할 일");

        final String label;

        Type(String label) {
            this.label = label;
        }
    }

    /** 검색 대상 한 건. */
    public static final class Item {
        public final Type type;
        public final long id;
        public final String title;
        public final String body;
        public final long time;
        public final boolean locked;
        public final boolean completed;

        public Item(Type type, long id, String title, String body, long time, boolean locked, boolean completed) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.body = body;
            this.time = time;
            this.locked = locked;
            this.completed = completed;
        }

        /** 잠긴 노트는 내용으로 찾히지 않아야 검색으로 내용을 짐작할 수 없다. */
        public boolean matches(SearchQuery query) {
            return locked ? query.matches(title) : query.matches(title, body);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Item item);
    }

    private final OnItemClickListener listener;
    private List<Item> items = new ArrayList<>();
    private SearchQuery query = SearchQuery.parse(null);

    public SearchResultAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setResults(List<Item> items, SearchQuery query) {
        this.items = items;
        this.query = query;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ResultViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvType;
        private final TextView tvTime;
        private final TextView tvTitle;
        private final TextView tvBody;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.M.d", Locale.KOREAN);

        ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tv_result_type);
            tvTime = itemView.findViewById(R.id.tv_result_time);
            tvTitle = itemView.findViewById(R.id.tv_result_title);
            tvBody = itemView.findViewById(R.id.tv_result_body);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(items.get(position));
                }
            });
        }

        void bind(Item item) {
            int highlight = ContextCompat.getColor(itemView.getContext(), R.color.color_accent);
            tvType.setText(item.completed ? item.type.label + " · 완료" : item.type.label);
            tvTime.setText(item.time > 0 ? dateFormat.format(new Date(item.time)) : "");

            if (item.type == Type.MEMO) {
                tvTitle.setVisibility(View.GONE);
            } else {
                tvTitle.setVisibility(View.VISIBLE);
                String title = item.title == null || item.title.isEmpty() ? "제목 없음" : item.title;
                tvTitle.setText(SearchHighlighter.highlight(title, query, highlight));
            }

            if (item.locked) {
                tvBody.setVisibility(View.VISIBLE);
                tvBody.setText("잠긴 노트입니다.");
            } else if (item.body == null || item.body.trim().isEmpty()) {
                tvBody.setVisibility(View.GONE);
            } else {
                tvBody.setVisibility(View.VISIBLE);
                tvBody.setText(SearchHighlighter.highlight(query.excerpt(item.body, EXCERPT_LENGTH), query, highlight));
            }
        }
    }
}
