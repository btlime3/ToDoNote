package kr.akotis.todonote.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.MemoAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.MemoItem;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class MemoFragment extends Fragment {

    private FloatingActionButton fabMemo;
    private View inputContainer;
    private TextInputEditText etMemoInput;
    private MaterialButton btnSave;
    private View btnCancel;
    private DatabaseHelper dbHelper;
    private View bottomNav;
    private RecyclerView recyclerMemo;
    private MemoAdapter memoAdapter;
    private MemoItem editingMemo;

    public MemoFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_memo, container, false);

        dbHelper = new DatabaseHelper(requireContext());

        // 하단 NavBar 참조
        if (getActivity() != null) {
            bottomNav = getActivity().findViewById(R.id.bottom_nav);
        }

        fabMemo = view.findViewById(R.id.fab_memo);
        inputContainer = view.findViewById(R.id.memo_input_container);
        etMemoInput = view.findViewById(R.id.et_memo_input);
        btnSave = view.findViewById(R.id.btn_memo_save);
        btnCancel = view.findViewById(R.id.btn_memo_cancel);
        recyclerMemo = view.findViewById(R.id.recycler_memo);

        // RecyclerView 설정
        memoAdapter = new MemoAdapter();
        recyclerMemo.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerMemo.setAdapter(memoAdapter);

        // 스와이프 제스처 설정
        setupSwipeGesture();

        // 메모 목록 로드
        loadMemos();

        fabMemo.setOnClickListener(v -> showInputContainer());

        btnSave.setOnClickListener(v -> saveMemo());

        btnCancel.setOnClickListener(v -> hideInputContainer());

        // 키보드가 올라올 때 입력창이 키보드 위에 위치하도록 설정
        setupKeyboardInsets(view);

        return view;
    }

    private void loadMemos() {
        List<MemoItem> memos = dbHelper.getAllMemos();
        memoAdapter.setMemos(memos);
    }

    private void showInputContainer() {
        inputContainer.setVisibility(View.VISIBLE);
        etMemoInput.requestFocus();
        
        // 하단 NavBar 숨기기
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
        
        // 키보드 표시
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etMemoInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideInputContainer() {
        inputContainer.setVisibility(View.GONE);
        etMemoInput.setText("");
        etMemoInput.clearFocus();
        editingMemo = null; // 수정 모드 해제
        
        // 하단 NavBar 표시
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
        
        // 키보드 숨기기
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMemoInput.getWindowToken(), 0);
        }
    }

    private void saveMemo() {
        String content = etMemoInput.getText().toString().trim();
        
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "메모 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingMemo != null) {
            // 수정 모드
            editingMemo.setContent(content);
            int result = dbHelper.updateMemo(editingMemo);
            if (result > 0) {
                loadMemos(); // 목록 새로고침
                //Toast.makeText(requireContext(), "메모가 수정되었습니다.", Toast.LENGTH_SHORT).show();
            }
            editingMemo = null;
        } else {
            // 새 메모 추가
            MemoItem memo = new MemoItem(content);
            long id = dbHelper.insertMemo(memo);
            
            if (id > 0) {
                memo.setId(id);
                // 새 메모는 상단에 추가되므로 목록을 다시 로드
                loadMemos();
            }
        }
        
        hideInputContainer();
    }

    private void setupSwipeGesture() {
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                memoAdapter.moveItem(fromPosition, toPosition);
                
                // 순서 변경 후 데이터베이스 업데이트
                dbHelper.updateMemoOrder(memoAdapter.getMemoList());
                
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                MemoItem memo = memoAdapter.getMemoAt(position);

                if (direction == ItemTouchHelper.LEFT) {
                    // 왼쪽 스와이프: 삭제
                    deleteMemo(memo, position);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // 오른쪽 스와이프: 수정
                    editMemo(memo);
                }
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    android.graphics.Paint paint = new android.graphics.Paint();
                    
                    if (dX > 0) {
                        // 오른쪽 스와이프 (수정) - 메인 컬러 배경
                        paint.setColor(0xFFA0D9C8); // color_primary
                        c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX, (float) itemView.getBottom(), paint);
                        
                        // 수정 아이콘
                        android.graphics.drawable.Drawable editIcon = requireContext().getDrawable(android.R.drawable.ic_menu_edit);
                        if (editIcon != null) {
                            int iconMargin = (itemView.getHeight() - editIcon.getIntrinsicHeight()) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + editIcon.getIntrinsicHeight();
                            int iconLeft = itemView.getLeft() + iconMargin;
                            int iconRight = iconLeft + editIcon.getIntrinsicWidth();
                            editIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            editIcon.draw(c);
                        }
                    } else {
                        // 왼쪽 스와이프 (삭제) - 보조 컬러 어두운 버전 사용
                        paint.setColor(0xFFE0B5A3); // color_secondary_dark
                        c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);
                        
                        // 삭제 아이콘
                        android.graphics.drawable.Drawable deleteIcon = requireContext().getDrawable(android.R.drawable.ic_menu_delete);
                        if (deleteIcon != null) {
                            int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                            int iconRight = itemView.getRight() - iconMargin;
                            int iconLeft = iconRight - deleteIcon.getIntrinsicWidth();
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            deleteIcon.draw(c);
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerMemo);
    }

    private void editMemo(MemoItem memo) {
        editingMemo = memo;
        etMemoInput.setText(memo.getContent());
        showInputContainer();
        // 목록 새로고침 (스와이프로 인해 제거된 항목 복원)
        loadMemos();
        
        // 커서를 텍스트 끝에 위치시키기
        etMemoInput.post(() -> {
            Editable text = etMemoInput.getText();
            if (text != null) {
                etMemoInput.setSelection(text.length());
            }
        });
    }

    private void deleteMemo(MemoItem memo, int position) {
        // 삭제 확인 다이얼로그 표시
        new AlertDialog.Builder(requireContext())
                .setTitle("메모 삭제")
                .setMessage("정말로 이 메모를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    int result = dbHelper.deleteMemo(memo.getId());
                    if (result > 0) {
                        memoAdapter.removeMemo(position);
                        //Toast.makeText(requireContext(), "메모가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        loadMemos(); // 실패 시 목록 새로고침
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    // 취소 시 목록 새로고침 (스와이프로 인해 제거된 항목 복원)
                    loadMemos();
                })
                .setCancelable(true)
                .setOnCancelListener(dialog -> {
                    // 다이얼로그가 취소되면 목록 새로고침
                    loadMemos();
                })
                .show();
    }

    private void setupKeyboardInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            if (imeInsets.bottom > 0) {
                // 키보드가 표시될 때 입력창을 키보드 위로 이동
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                params.bottomMargin = imeInsets.bottom;
                v.setLayoutParams(params);
            } else {
                // 키보드가 숨겨질 때 마진 제거 및 입력창 닫기
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                params.bottomMargin = 0;
                v.setLayoutParams(params);
                
                // 키보드가 닫혔을 때 입력창이 보이는 상태면 닫기
                if (inputContainer.getVisibility() == View.VISIBLE) {
                    hideInputContainer();
                }
            }
            return insets;
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

