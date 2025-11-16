package kr.akotis.todonote;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ToDoAdapter extends RecyclerView.Adapter<ToDoAdapter.ToDoViewHolder> {

    private List<ToDoItem> todoList;
    private OnToDoClickListener listener;
    private OnToDoCheckedChangeListener checkedListener;

    public interface OnToDoClickListener {
        void onToDoClick(ToDoItem todo);
    }

    public interface OnToDoCheckedChangeListener {
        void onCheckedChanged(ToDoItem todo, boolean isChecked);
    }

    public ToDoAdapter() {
        this.todoList = new ArrayList<>();
    }

    public void setOnToDoClickListener(OnToDoClickListener listener) {
        this.listener = listener;
    }

    public void setOnToDoCheckedChangeListener(OnToDoCheckedChangeListener listener) {
        this.checkedListener = listener;
    }

    @NonNull
    @Override
    public ToDoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_todo, parent, false);
        return new ToDoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToDoViewHolder holder, int position) {
        ToDoItem todo = todoList.get(position);
        holder.bind(todo);
    }

    @Override
    public int getItemCount() {
        return todoList.size();
    }

    public void setToDos(List<ToDoItem> todos) {
        this.todoList = todos;
        notifyDataSetChanged();
    }

    public void removeToDo(int position) {
        if (position >= 0 && position < todoList.size()) {
            todoList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public ToDoItem getToDoAt(int position) {
        if (position >= 0 && position < todoList.size()) {
            return todoList.get(position);
        }
        return null;
    }

    private String getRelativeDateString(long dateMillis) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar targetDate = Calendar.getInstance();
        targetDate.setTimeInMillis(dateMillis);
        targetDate.set(Calendar.HOUR_OF_DAY, 0);
        targetDate.set(Calendar.MINUTE, 0);
        targetDate.set(Calendar.SECOND, 0);
        targetDate.set(Calendar.MILLISECOND, 0);

        long diffInMillis = targetDate.getTimeInMillis() - today.getTimeInMillis();
        int diffInDays = (int) (diffInMillis / (24 * 60 * 60 * 1000));

        if (diffInDays == 0) {
            return "오늘";
        } else if (diffInDays == 1) {
            return "내일";
        } else if (diffInDays == -1) {
            return "어제";
        } else if (diffInDays > 1) {
            return diffInDays + "일 후";
        } else {
            return Math.abs(diffInDays) + "일 전";
        }
    }

    class ToDoViewHolder extends RecyclerView.ViewHolder {
        private MaterialCheckBox checkboxTodo;
        private TextView tvTitle;
        private TextView tvDetail;
        private TextView tvDate;
        private TextView tvTime;

        public ToDoViewHolder(@NonNull View itemView) {
            super(itemView);
            checkboxTodo = itemView.findViewById(R.id.checkbox_todo);
            tvTitle = itemView.findViewById(R.id.tv_todo_title);
            tvDetail = itemView.findViewById(R.id.tv_todo_detail);
            tvDate = itemView.findViewById(R.id.tv_todo_date);
            tvTime = itemView.findViewById(R.id.tv_todo_time);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onToDoClick(todoList.get(position));
                    }
                }
            });
        }

        public void bind(ToDoItem todo) {
            // 리스너를 일시적으로 제거하여 bind 중 리스너 트리거 방지
            checkboxTodo.setOnCheckedChangeListener(null);
            checkboxTodo.setChecked(false); // 완료는 삭제로 처리하므로 항상 false
            checkboxTodo.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (checkedListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        checkedListener.onCheckedChanged(todoList.get(position), isChecked);
                    }
                }
            });
            
            tvTitle.setText(todo.getTitle());
            tvTitle.setAlpha(1.0f);
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            
            if (todo.getDetail() != null && !todo.getDetail().isEmpty()) {
                tvDetail.setText(todo.getDetail());
                tvDetail.setVisibility(View.VISIBLE);
                tvDetail.setAlpha(1.0f);
                tvDetail.setPaintFlags(tvDetail.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tvDetail.setVisibility(View.GONE);
            }

            // 날짜와 상대적 날짜 표시
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault());
            String dateStr = dateFormat.format(todo.getDate());
            String relativeStr = getRelativeDateString(todo.getDate());
            tvDate.setText(dateStr + " (" + relativeStr + ")");

            // 시간 포맷
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvTime.setText(timeFormat.format(todo.getTime()));
        }
    }
}
