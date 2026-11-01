package kr.akotis.todonote.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.TodayInfoItem;

public class TodayInfoAdapter extends RecyclerView.Adapter<TodayInfoAdapter.InfoViewHolder> {

    private final List<TodayInfoItem> items;

    public TodayInfoAdapter(List<TodayInfoItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public InfoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_today_info, parent, false);
        return new InfoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InfoViewHolder holder, int position) {
        TodayInfoItem item = items.get(position);
        holder.tvCategory.setText(item.getCategory());
        holder.tvTitle.setText(item.getTitle());
        holder.tvSummary.setText(item.getSummary());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class InfoViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategory;
        final TextView tvTitle;
        final TextView tvSummary;

        InfoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_info_category);
            tvTitle = itemView.findViewById(R.id.tv_info_title);
            tvSummary = itemView.findViewById(R.id.tv_info_summary);
        }
    }
}
