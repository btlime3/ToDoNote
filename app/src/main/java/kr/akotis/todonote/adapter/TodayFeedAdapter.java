package kr.akotis.todonote.adapter;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.TodayItem;
import kr.akotis.todonote.util.TodayThumbnailLoader;

/** 소식을 날짜별로 묶어 보여 준다. 목록 맨 위 머리글에는 마지막 수신 시각을 함께 표시한다. */
public class TodayFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final float READ_ALPHA = 0.55f;
    private static final long NEW_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final String AI_LABEL = "AI 요약 · ";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);

    public interface OnItemClickListener {
        void onItemClick(TodayItem item, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TodayItem item, int position);
    }

    private static final class DayHeader {
        final String title;
        final String caption;

        DayHeader(String title, String caption) {
            this.title = title;
            this.caption = caption;
        }
    }

    private final OnItemClickListener listener;
    private final OnItemLongClickListener longClickListener;
    private final List<Object> rows = new ArrayList<>();
    private Set<String> savedUrls = Collections.emptySet();
    private boolean highlightNew = true;

    public TodayFeedAdapter(OnItemClickListener listener, OnItemLongClickListener longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setHighlightNew(boolean highlightNew) {
        this.highlightNew = highlightNew;
    }

    /** 카드의 보관 표시에 쓴다. 화면이 가진 집합을 그대로 참조한다. */
    public void setSavedUrls(Set<String> savedUrls) {
        this.savedUrls = savedUrls;
    }

    /** @return 그 위치의 소식. 날짜 머리글이거나 범위를 벗어나면 null */
    public TodayItem itemAt(int position) {
        if (position < 0 || position >= rows.size()) {
            return null;
        }
        Object row = rows.get(position);
        return row instanceof TodayItem ? (TodayItem) row : null;
    }

    public boolean isSaved(TodayItem item) {
        return savedUrls.contains(item.getUrl());
    }

    /** 다른 탭에서 저장 상태가 바뀔 수 있으므로 주소로 찾아 다시 그린다. */
    public void refreshItem(String url) {
        for (int i = 0; i < rows.size(); i++) {
            TodayItem item = itemAt(i);
            if (item != null && url.equals(item.getUrl())) {
                notifyItemChanged(i);
            }
        }
    }

    /** 날짜 묶음 없이 주어진 순서대로 보여 준다. */
    public void setPlainItems(List<TodayItem> items) {
        rows.clear();
        rows.addAll(items);
        notifyDataSetChanged();
    }

    public void setItems(List<TodayItem> items, String lastReceived) {
        rows.clear();
        LocalDate today = LocalDate.now();
        LocalDate currentDay = null;
        for (TodayItem item : items) {
            LocalDate day = localDate(item.getDisplayTime());
            if (!day.equals(currentDay)) {
                rows.add(new DayHeader(dayTitle(day, today), rows.isEmpty() ? lastReceived : ""));
                currentDay = day;
            }
            rows.add(item);
        }
        notifyDataSetChanged();
    }

    private static LocalDate localDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String dayTitle(LocalDate day, LocalDate today) {
        String date = day.format(DAY_FORMAT);
        if (day.equals(today)) {
            return "오늘 · " + date;
        }
        return day.equals(today.minusDays(1)) ? "어제 · " + date : date;
    }

    static String relativeTime(long millis, long now) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(now - millis, 0));
        if (minutes < 1) {
            return "방금";
        } else if (minutes < 60) {
            return minutes + "분 전";
        } else if (minutes < TimeUnit.DAYS.toMinutes(1)) {
            return TimeUnit.MINUTES.toHours(minutes) + "시간 전";
        }
        return TimeUnit.MINUTES.toDays(minutes) + "일 전";
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof DayHeader ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_today_header, parent, false));
        }
        return new ItemViewHolder(inflater.inflate(R.layout.item_today, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((DayHeader) row);
        } else {
            ((ItemViewHolder) holder).bind((TodayItem) row);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvCaption;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_today_day);
            tvCaption = itemView.findViewById(R.id.tv_today_last_received);
        }

        void bind(DayHeader header) {
            tvTitle.setText(header.title);
            tvCaption.setText(header.caption);
        }
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSource;
        private final TextView tvTime;
        private final TextView tvTitle;
        private final TextView tvSummary;
        private final ImageView ivThumbnail;
        private final ImageView ivSaved;
        private final View content;
        private final MaterialCardView card;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSource = itemView.findViewById(R.id.tv_today_source);
            tvTime = itemView.findViewById(R.id.tv_today_time);
            tvTitle = itemView.findViewById(R.id.tv_today_title);
            tvSummary = itemView.findViewById(R.id.tv_today_summary);
            ivThumbnail = itemView.findViewById(R.id.iv_today_thumbnail);
            ivSaved = itemView.findViewById(R.id.iv_today_saved);
            content = itemView.findViewById(R.id.today_item_content);
            card = (MaterialCardView) itemView;
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick((TodayItem) rows.get(position), position);
                }
            });
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }
                longClickListener.onItemLongClick((TodayItem) rows.get(position), position);
                return true;
            });
        }

        void bind(TodayItem item) {
            tvSource.setText(item.getSource());
            tvTime.setText(relativeTime(item.getDisplayTime(), System.currentTimeMillis()));
            tvTitle.setText(item.getTitle());
            boolean hasSummary = item.getSummary() != null && !item.getSummary().isEmpty();
            tvSummary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            tvSummary.setText(hasSummary ? summaryText(item) : "");
            TodayThumbnailLoader.load(ivThumbnail, item.getImageUrl());
            ivSaved.setVisibility(highlightNew && isSaved(item) ? View.VISIBLE : View.GONE);
            // 새 소식은 배경으로 눈에 띄게 하고, 읽은 항목은 흐리게 표시한다.
            card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(),
                    highlightNew && isNew(item) ? R.color.color_today_new : R.color.color_surface));
            content.setAlpha(item.isRead() ? READ_ALPHA : 1f);
        }

        private CharSequence summaryText(TodayItem item) {
            if (!item.isAiSummary()) {
                return item.getSummary();
            }
            SpannableStringBuilder text = new SpannableStringBuilder(AI_LABEL).append(item.getSummary());
            int color = ContextCompat.getColor(itemView.getContext(), R.color.color_primary_text);
            text.setSpan(new ForegroundColorSpan(color), 0, AI_LABEL.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, AI_LABEL.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }

        /** 아직 읽지 않았고, 오늘 나온 글이거나 하루 안에 받은 글이다. */
        private boolean isNew(TodayItem item) {
            if (item.isRead()) {
                return false;
            }
            return localDate(item.getDisplayTime()).equals(LocalDate.now())
                    || System.currentTimeMillis() - item.getReceivedAt() < NEW_WINDOW_MILLIS;
        }
    }
}
