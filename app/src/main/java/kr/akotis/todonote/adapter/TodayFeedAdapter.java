package kr.akotis.todonote.adapter;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import java.util.HashSet;
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
    /** 시스템 기본값(0.4초)은 목록을 넘기다 잘못 눌리기 쉬워서 더 오래 눌렀을 때만 보관한다. */
    private static final long SAVE_PRESS_MILLIS = 800;
    private static final long NEW_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(1);
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
    /** AI 요약을 펼친 글의 주소. 새 소식이 도착해 목록을 다시 그려도 펼친 상태를 유지한다. */
    private final Set<String> expandedAi = new HashSet<>();
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

    /** 다른 탭에서 저장 상태가 바뀔 수 있으므로 주소로 찾아 다시 그린다. */
    public void refreshItem(String url) {
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row instanceof TodayItem && url.equals(((TodayItem) row).getUrl())) {
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
        private final View boxAi;
        private final View aiToggle;
        private final ImageView ivAiArrow;
        private final TextView tvAi;
        private final MaterialCardView card;
        private final Runnable savePress = this::onSavePress;
        private boolean ignoreClick;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSource = itemView.findViewById(R.id.tv_today_source);
            tvTime = itemView.findViewById(R.id.tv_today_time);
            tvTitle = itemView.findViewById(R.id.tv_today_title);
            tvSummary = itemView.findViewById(R.id.tv_today_summary);
            ivThumbnail = itemView.findViewById(R.id.iv_today_thumbnail);
            ivSaved = itemView.findViewById(R.id.iv_today_saved);
            boxAi = itemView.findViewById(R.id.box_today_ai);
            aiToggle = itemView.findViewById(R.id.today_ai_toggle);
            ivAiArrow = itemView.findViewById(R.id.iv_today_ai_arrow);
            tvAi = itemView.findViewById(R.id.tv_today_ai);
            card = (MaterialCardView) itemView;
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (!ignoreClick && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick((TodayItem) rows.get(position), position);
                }
            });
            // 요약 칸을 눌러도 링크를 열거나 읽음으로 바꾸지 않고 칸만 여닫는다.
            aiToggle.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (!ignoreClick && position != RecyclerView.NO_POSITION) {
                    toggleAi(((TodayItem) rows.get(position)).getUrl());
                }
            });
            watchSavePress(itemView);
            watchSavePress(aiToggle);
        }

        private void toggleAi(String url) {
            boolean expanded = expandedAi.add(url);
            if (!expanded) {
                expandedAi.remove(url);
            }
            showAi(expanded);
            if (expanded) {
                // 화면 아래쪽 카드는 펼친 내용이 가려지므로 보이는 곳까지 올린다.
                itemView.post(() -> itemView.requestRectangleOnScreen(
                        new Rect(0, 0, itemView.getWidth(), itemView.getHeight())));
            }
        }

        private void showAi(boolean expanded) {
            tvAi.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ivAiArrow.setRotation(expanded ? 180 : 0);
            aiToggle.setContentDescription(expanded ? "AI 요약 닫기" : "AI 요약 열기");
        }

        /** 눌림 효과와 짧은 클릭은 뷰가 그대로 처리하도록 터치를 소비하지 않는다. */
        @SuppressLint("ClickableViewAccessibility")
        private void watchSavePress(View target) {
            int touchSlop = ViewConfiguration.get(target.getContext()).getScaledTouchSlop();
            float[] down = new float[2];
            target.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        ignoreClick = false;
                        down[0] = event.getX();
                        down[1] = event.getY();
                        v.postDelayed(savePress, SAVE_PRESS_MILLIS);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // 카드 안에서 옆으로 밀다 떼어도 카드는 클릭으로 처리하므로 여기서 막는다.
                        if (Math.hypot(event.getX() - down[0], event.getY() - down[1]) > touchSlop) {
                            v.removeCallbacks(savePress);
                            ignoreClick = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(savePress);
                        break;
                    default:
                        break;
                }
                return false;
            });
        }

        private void onSavePress() {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                // 보관한 뒤 손을 뗄 때 링크가 열리지 않게 한다.
                ignoreClick = true;
                itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                longClickListener.onItemLongClick((TodayItem) rows.get(position), position);
            }
        }

        void bind(TodayItem item) {
            // 누르는 동안 새 소식이 도착해 다른 글로 바뀌면 그 글이 보관되지 않게 한다.
            itemView.removeCallbacks(savePress);
            tvSource.setText(item.getSource());
            tvTime.setText(relativeTime(item.getDisplayTime(), System.currentTimeMillis()));
            tvTitle.setText(item.getTitle());
            String summary = item.getDisplaySummary();
            boolean hasSummary = summary != null && !summary.isEmpty();
            tvSummary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            tvSummary.setText(hasSummary ? summary : "");
            String aiText = item.getDisplayAiText();
            boxAi.setVisibility(aiText.isEmpty() ? View.GONE : View.VISIBLE);
            tvAi.setText(aiText);
            showAi(expandedAi.contains(item.getUrl()));
            TodayThumbnailLoader.load(ivThumbnail, item.getImageUrl());
            ivSaved.setVisibility(highlightNew && savedUrls.contains(item.getUrl()) ? View.VISIBLE : View.GONE);
            // 새 소식은 배경으로 눈에 띄게 한다. 읽은 글은 제목 굵기만 줄여 흐려지지 않게 구분한다.
            card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(),
                    highlightNew && isNew(item) ? R.color.color_today_new : R.color.color_surface));
            tvTitle.setTypeface(Typeface.DEFAULT, item.isRead() ? Typeface.NORMAL : Typeface.BOLD);
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
