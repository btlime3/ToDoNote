package kr.akotis.todonote.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.TodayItem;

public class TodayFeedAdapter extends RecyclerView.Adapter<TodayFeedAdapter.ItemViewHolder> {
    private static final float READ_ALPHA = 0.55f;

    public interface OnItemClickListener {
        void onItemClick(TodayItem item, int position);
    }

    private final OnItemClickListener listener;
    private List<TodayItem> items = new ArrayList<>();

    public TodayFeedAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<TodayItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_today, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSource;
        private final TextView tvTime;
        private final TextView tvTitle;
        private final TextView tvSummary;
        private final View content;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSource = itemView.findViewById(R.id.tv_today_source);
            tvTime = itemView.findViewById(R.id.tv_today_time);
            tvTitle = itemView.findViewById(R.id.tv_today_title);
            tvSummary = itemView.findViewById(R.id.tv_today_summary);
            content = itemView.findViewById(R.id.today_item_content);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(items.get(position), position);
                }
            });
        }

        void bind(TodayItem item) {
            String category = TodayItem.categoryLabel(item.getCategory());
            String source = item.getSource();
            tvSource.setText(source == null || source.isEmpty() ? category : category + " · " + source);
            tvTime.setText(DateUtils.getRelativeTimeSpanString(item.getDisplayTime(),
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
            tvTitle.setText(item.getTitle());
            boolean hasSummary = item.getSummary() != null && !item.getSummary().isEmpty();
            tvSummary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            tvSummary.setText(hasSummary ? item.getSummary() : "");
            // 읽은 항목은 흐리게 표시해 새 소식을 찾기 쉽게 한다.
            content.setAlpha(item.isRead() ? READ_ALPHA : 1f);
        }
    }
}
