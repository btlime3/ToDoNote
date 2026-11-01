package kr.akotis.todonote.adapter;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.R;
import kr.akotis.todonote.model.MessageItem;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<MessageItem> messageList;

    public MessageAdapter() {
        this.messageList = new ArrayList<>();
    }

    public void setMessages(List<MessageItem> messages) {
        this.messageList = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageItem message = messageList.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public MessageItem getMessageAt(int position) {
        if (position >= 0 && position < messageList.size()) {
            return messageList.get(position);
        }
        return null;
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvDate;
        private TextView tvTime;
        private TextView tvTitle;
        private TextView tvBody;
        private androidx.cardview.widget.CardView cardView;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.textViewDate);
            tvTime = itemView.findViewById(R.id.textViewTime);
            tvTitle = itemView.findViewById(R.id.textViewTitle);
            tvBody = itemView.findViewById(R.id.textViewBody);
            cardView = itemView.findViewById(R.id.cardView);
        }

        public void bind(MessageItem message) {
            tvTitle.setText(message.getTitle() != null ? message.getTitle() : "");
            
            // 본문 텍스트 설정 (HTML 처리)
            String body = message.getBody() != null ? message.getBody() : "";
            
            // HTML로 렌더링 (줄바꿈은 <br>, 링크는 <a href> 태그 사용)
            tvBody.setText(HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY));
            
            // 링크 색상을 파란색으로 설정
            tvBody.setLinkTextColor(itemView.getContext().getResources().getColor(R.color.link_color, null));
            
            // 링크 클릭을 위해 MovementMethod 설정
            tvBody.setMovementMethod(LinkMovementMethod.getInstance());

            // 날짜 및 시간 포맷
            Calendar messageCalendar = Calendar.getInstance();
            messageCalendar.setTimeInMillis(message.getReceivedAt());

            Calendar todayCalendar = Calendar.getInstance();
            todayCalendar.set(Calendar.HOUR_OF_DAY, 0);
            todayCalendar.set(Calendar.MINUTE, 0);
            todayCalendar.set(Calendar.SECOND, 0);
            todayCalendar.set(Calendar.MILLISECOND, 0);

            Calendar messageDateCalendar = Calendar.getInstance();
            messageDateCalendar.setTimeInMillis(message.getReceivedAt());
            messageDateCalendar.set(Calendar.HOUR_OF_DAY, 0);
            messageDateCalendar.set(Calendar.MINUTE, 0);
            messageDateCalendar.set(Calendar.SECOND, 0);
            messageDateCalendar.set(Calendar.MILLISECOND, 0);

            // 오늘 날짜인지 확인
            boolean isToday = messageDateCalendar.getTimeInMillis() == todayCalendar.getTimeInMillis();

            // 오늘 알림인 경우 배경색 변경 (연한 navbar 색상)
            if (isToday) {
                cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.color_primary_very_light, null));
            } else {
                // 지난 알림은 하얀색 배경
                cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.color_surface, null));
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN);
            SimpleDateFormat timeFormat = new SimpleDateFormat("a h:mm", Locale.KOREAN);

            tvDate.setText(dateFormat.format(messageCalendar.getTime()));
            tvTime.setText(timeFormat.format(messageCalendar.getTime()));
        }
    }
}

