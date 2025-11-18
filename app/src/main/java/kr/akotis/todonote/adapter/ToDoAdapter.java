package kr.akotis.todonote.adapter;

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

import kr.akotis.todonote.R;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.ToDoItem;

public class ToDoAdapter extends RecyclerView.Adapter<ToDoAdapter.ToDoViewHolder> {

    private List<ToDoItem> todoList;
    private OnToDoClickListener listener;
    private OnToDoCheckedChangeListener checkedListener;
    private DatabaseHelper dbHelper;

    public interface OnToDoClickListener {
        void onToDoClick(ToDoItem todo);
    }

    public interface OnToDoCheckedChangeListener {
        void onCheckedChanged(ToDoItem todo, boolean isChecked);
    }

    public ToDoAdapter() {
        this.todoList = new ArrayList<>();
    }

    public void setDatabaseHelper(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
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
        // 데이터베이스에서 읽어온 체크 상태를 화면에 반영
        holder.bind(todo);
    }

    @Override
    public int getItemCount() {
        return todoList.size();
    }

    public void setToDos(List<ToDoItem> todos) {
        this.todoList = todos != null ? todos : new ArrayList<>();
        // notifyDataSetChanged()를 호출하여 모든 ViewHolder가 재바인딩되도록 함
        // 이때 bind() 메서드에서 데이터베이스의 체크 상태가 체크박스에 반영됨
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
        private com.google.android.material.card.MaterialCardView cardView;

        public ToDoViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_todo);
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
            // 체크박스 리스너를 먼저 제거하여 setChecked 호출 시 리스너가 트리거되지 않도록 함
            checkboxTodo.setOnCheckedChangeListener(null);
            
            // 데이터베이스에서 읽어온 체크 상태를 체크박스에 설정
            boolean isChecked = todo.isChecked();
            android.util.Log.d("ToDoAdapter", "bind: todo id=" + todo.getId() + ", title=" + todo.getTitle() + ", isChecked=" + isChecked);
            
            // 체크박스에 현재 todo 객체를 태그로 저장 (ViewHolder 재사용 시 확인용)
            checkboxTodo.setTag(todo);
            
            // 체크 상태를 체크박스에 무조건 설정 (데이터베이스의 체크 상태를 화면에 반영)
            // RecyclerView ViewHolder 재사용으로 인해 이전 상태가 남아있을 수 있으므로 항상 설정
            checkboxTodo.setChecked(isChecked);
            
            // 체크박스 상태 강제 업데이트
            checkboxTodo.jumpDrawablesToCurrentState();
            checkboxTodo.refreshDrawableState();
            
            // 체크박스 클릭 활성화
            checkboxTodo.setClickable(true);
            checkboxTodo.setFocusable(true);
            
            // 체크박스 클릭 시 체크 상태 저장 및 UI 업데이트
            checkboxTodo.setOnCheckedChangeListener((buttonView, checked) -> {
                // 태그에서 todo 객체 가져오기
                ToDoItem currentTodo = (ToDoItem) buttonView.getTag();
                if (currentTodo == null || currentTodo.getId() != todo.getId()) {
                    // ViewHolder 재사용으로 인한 불일치 방지
                    return;
                }
                
                // 체크 상태 업데이트
                todo.setChecked(checked);
                // 데이터베이스에 저장
                if (dbHelper != null) {
                    dbHelper.updateToDo(todo);
                }
                // UI 업데이트 (취소선)
                updateTextStrikethrough(todo, checked);
                // 알파값 조정
                if (!todo.isCompleted()) {
                    tvTitle.setAlpha(checked ? 0.5f : 1.0f);
                    tvDate.setAlpha(checked ? 0.5f : 1.0f);
                    tvTime.setAlpha(checked ? 0.5f : 1.0f);
                }
                // 외부 리스너 호출 (완료 상태 변경 등)
                if (checkedListener != null) {
                    checkedListener.onCheckedChanged(todo, checked);
                }
            });
            
            // 체크 상태에 따라 취소선 표시
            updateTextStrikethrough(todo, isChecked);
            
            // 날짜별 스타일 적용
            applyDateBasedStyle(todo);
            
            // 완료된 항목은 취소선 표시 (완료 상태는 별도)
            boolean isCompleted = todo.isCompleted();
            if (isCompleted) {
                tvTitle.setAlpha(0.5f);
                tvDate.setAlpha(0.5f);
                tvTime.setAlpha(0.5f);
            } else {
                // 체크 상태에 따라 알파값 조정
                tvTitle.setAlpha(isChecked ? 0.5f : 1.0f);
                tvDate.setAlpha(isChecked ? 0.5f : 1.0f);
                tvTime.setAlpha(isChecked ? 0.5f : 1.0f);
            }
        }
        
        private void applyDateBasedStyle(ToDoItem todo) {
            Calendar now = Calendar.getInstance();
            Calendar todoDateTime = Calendar.getInstance();
            todoDateTime.setTimeInMillis(todo.getDate());
            Calendar todoTime = Calendar.getInstance();
            todoTime.setTimeInMillis(todo.getTime());
            todoDateTime.set(Calendar.HOUR_OF_DAY, todoTime.get(Calendar.HOUR_OF_DAY));
            todoDateTime.set(Calendar.MINUTE, todoTime.get(Calendar.MINUTE));
            todoDateTime.set(Calendar.SECOND, 0);
            todoDateTime.set(Calendar.MILLISECOND, 0);
            
            // 오늘 날짜인지 확인 (날짜만 비교)
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            
            Calendar todoDate = Calendar.getInstance();
            todoDate.setTimeInMillis(todo.getDate());
            todoDate.set(Calendar.HOUR_OF_DAY, 0);
            todoDate.set(Calendar.MINUTE, 0);
            todoDate.set(Calendar.SECOND, 0);
            todoDate.set(Calendar.MILLISECOND, 0);
            
            boolean isToday = today.getTimeInMillis() == todoDate.getTimeInMillis();
            boolean isPast = todoDateTime.getTimeInMillis() < now.getTimeInMillis();
            
            if (cardView != null) {
                if (isToday) {
                    // 오늘인 경우 하이라이트 배경
                    cardView.setCardBackgroundColor(itemView.getContext().getColor(R.color.color_accent_light));
                    // 텍스트 색상 기본값 유지
                    tvTitle.setTextColor(itemView.getContext().getColor(R.color.color_text_dark));
                    tvDate.setTextColor(itemView.getContext().getColor(R.color.color_text_medium));
                    tvTime.setTextColor(itemView.getContext().getColor(R.color.color_text_medium));
                } else if (isPast) {
                    // 지난 경우 회색 배경 및 텍스트
                    cardView.setCardBackgroundColor(0xFFE0E0E0); // 연한 회색
                    tvTitle.setTextColor(0xFF999999); // 회색 텍스트
                    tvDate.setTextColor(0xFF999999);
                    tvTime.setTextColor(0xFF999999);
                } else {
                    // 기본 색상
                    cardView.setCardBackgroundColor(itemView.getContext().getColor(R.color.color_surface));
                    tvTitle.setTextColor(itemView.getContext().getColor(R.color.color_text_dark));
                    tvDate.setTextColor(itemView.getContext().getColor(R.color.color_text_medium));
                    tvTime.setTextColor(itemView.getContext().getColor(R.color.color_text_medium));
                }
            }
        }
        
        private void updateTextStrikethrough(ToDoItem todo, boolean isChecked) {
            // 제목 취소선
            tvTitle.setText(todo.getTitle());
            if (isChecked) {
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            
            // 상세 취소선
            if (todo.getDetail() != null && !todo.getDetail().isEmpty()) {
                tvDetail.setText(todo.getDetail());
                tvDetail.setVisibility(View.VISIBLE);
                if (isChecked) {
                    tvDetail.setPaintFlags(tvDetail.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    tvDetail.setPaintFlags(tvDetail.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                }
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

