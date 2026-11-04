package kr.akotis.todonote.adapter;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Function;

import kr.akotis.todonote.model.TodayItem;

/** 소식 카드를 왼쪽으로 밀 때의 동작. 뒤에 무엇을 하는지 글자로 보여 주고, 날짜 머리글은 밀리지 않는다. */
public class TodaySwipeCallback extends ItemTouchHelper.SimpleCallback {
    public interface OnSwipeListener {
        void onSwipe(TodayItem item, int position);
    }

    private static final float CORNER_DP = 12f;
    private static final float MARGIN_DP = 12f;
    private static final float TEXT_SP = 14f;

    private final TodayFeedAdapter adapter;
    private final Function<TodayItem, String> label;
    private final OnSwipeListener listener;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    public TodaySwipeCallback(TodayFeedAdapter adapter, int color, Function<TodayItem, String> label,
                              OnSwipeListener listener) {
        super(0, ItemTouchHelper.LEFT);
        this.adapter = adapter;
        this.label = label;
        this.listener = listener;
        background.setColor(color);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setFakeBoldText(true);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        return adapter.itemAt(viewHolder.getBindingAdapterPosition()) != null
                ? super.getSwipeDirs(recyclerView, viewHolder) : 0;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        TodayItem item = adapter.itemAt(position);
        if (item != null) {
            listener.onSwipe(item, position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        TodayItem item = adapter.itemAt(viewHolder.getBindingAdapterPosition());
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0 && item != null) {
            View card = viewHolder.itemView;
            float density = recyclerView.getResources().getDisplayMetrics().density;
            float scaledDensity = TEXT_SP * recyclerView.getResources().getDisplayMetrics().scaledDensity;
            bounds.set(card.getRight() + dX, card.getTop(), card.getRight(), card.getBottom());
            canvas.drawRoundRect(bounds, CORNER_DP * density, CORNER_DP * density, background);
            text.setTextSize(scaledDensity);
            canvas.drawText(label.apply(item), card.getRight() - MARGIN_DP * 2 * density,
                    card.getTop() + card.getHeight() / 2f + scaledDensity / 3f, text);
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
