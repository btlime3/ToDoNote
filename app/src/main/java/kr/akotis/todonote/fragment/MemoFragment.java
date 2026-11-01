package kr.akotis.todonote.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.MemoAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.DraftItem;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.util.DebouncedRunner;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class MemoFragment extends Fragment {

    private static final long DRAFT_SAVE_DELAY_MS = 1500;

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
    private DebouncedRunner draftSaver;
    private OnBackPressedCallback backCallback;
    /** 키보드가 실제로 열렸다가 닫힌 경우만 구분하기 위한 상태. */
    private boolean keyboardWasVisible;

    public MemoFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_memo, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        draftSaver = new DebouncedRunner(DRAFT_SAVE_DELAY_MS, this::saveDraftNow);

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

        fabMemo.setOnClickListener(v -> showInputContainer(true));

        btnSave.setOnClickListener(v -> saveMemo());

        btnCancel.setOnClickListener(v -> requestCloseInput());

        etMemoInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                draftSaver.schedule();
            }
        });

        // 키보드가 올라올 때 입력창이 키보드 위에 위치하도록 설정
        setupKeyboardInsets();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                requestCloseInput();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
        restoreDraft(savedInstanceState == null);
    }

    @Override
    public void onPause() {
        super.onPause();
        // 다른 앱으로 전환한 뒤 시스템이 앱을 종료해도 내용이 남도록 바로 기록한다.
        draftSaver.cancel();
        saveDraftNow();
    }

    /** 입력창이 열려 있는 동안 저장되지 않은 내용을 임시 저장본으로 유지한다. */
    private void saveDraftNow() {
        if (inputContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        if (hasUnsavedInput()) {
            long targetId = editingMemo != null ? editingMemo.getId() : DatabaseHelper.DRAFT_NEW_ITEM;
            dbHelper.saveMemoDraft(targetId, currentInput());
        } else {
            dbHelper.deleteMemoDraft();
        }
    }

    private void restoreDraft(boolean notifyUser) {
        DraftItem draft = dbHelper.getMemoDraft();
        if (draft == null) {
            return;
        }
        if (draft.getContent().trim().isEmpty()) {
            dbHelper.deleteMemoDraft();
            return;
        }
        // 수정하던 메모가 그 사이 삭제되었으면 내용을 잃지 않도록 새 메모로 이어서 쓴다.
        MemoItem target = draft.getTargetId() != DatabaseHelper.DRAFT_NEW_ITEM
                ? dbHelper.getMemoById(draft.getTargetId()) : null;
        editingMemo = target != null && target.getDeletedAt() == 0 ? target : null;
        if (editingMemo != null && draft.getContent().trim().equals(editingMemo.getContent().trim())) {
            editingMemo = null;
            dbHelper.deleteMemoDraft();
            return;
        }
        etMemoInput.setText(draft.getContent());
        etMemoInput.setSelection(draft.getContent().length());
        showInputContainer(false);
        if (notifyUser) {
            Toast.makeText(requireContext(), "작성 중이던 메모를 불러왔습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String currentInput() {
        return etMemoInput.getText() != null ? etMemoInput.getText().toString() : "";
    }

    private boolean hasUnsavedInput() {
        String original = editingMemo != null ? editingMemo.getContent().trim() : "";
        return !currentInput().trim().equals(original);
    }

    /** 닫기 버튼과 뒤로가기는 입력한 내용이 있으면 먼저 확인한다. */
    private void requestCloseInput() {
        if (!hasUnsavedInput()) {
            discardInput();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("메모 작성 취소")
                .setMessage("작성 중인 내용을 버리시겠습니까?")
                .setPositiveButton("버리기", (dialog, which) -> discardInput())
                .setNegativeButton("계속 작성", null)
                .show();
    }

    private void discardInput() {
        dbHelper.deleteMemoDraft();
        hideInputContainer();
    }

    private void loadMemos() {
        List<MemoItem> memos = dbHelper.getAllMemos();
        memoAdapter.setMemos(memos);
    }

    private void showInputContainer(boolean showKeyboard) {
        inputContainer.setVisibility(View.VISIBLE);
        fabMemo.hide();
        backCallback.setEnabled(true);

        // 하단 NavBar 숨기기
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }

        if (showKeyboard) {
            etMemoInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etMemoInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideInputContainer() {
        draftSaver.cancel();
        inputContainer.setVisibility(View.GONE);
        etMemoInput.setText("");
        draftSaver.cancel(); // 위 setText로 예약된 저장을 취소
        etMemoInput.clearFocus();
        editingMemo = null; // 수정 모드 해제
        fabMemo.show();
        backCallback.setEnabled(false);

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

        dbHelper.deleteMemoDraft();
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
        // 목록 새로고침 (스와이프로 인해 제거된 항목 복원)
        loadMemos();
        if (inputContainer.getVisibility() == View.VISIBLE && hasUnsavedInput()) {
            Toast.makeText(requireContext(), "작성 중인 메모를 먼저 저장하거나 취소해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        editingMemo = memo;
        etMemoInput.setText(memo.getContent());
        showInputContainer(true);
        
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
                        if (editingMemo != null && editingMemo.getId() == memo.getId()) {
                            // 수정 중이던 메모를 지웠으면 입력창도 함께 닫는다.
                            discardInput();
                        }
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

    private void setupKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 키보드가 있으면 그 위에, 없으면 시스템 내비게이션 바 위에 입력창을 둔다.
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = Math.max(imeInsets.bottom, systemBars.bottom);
            v.setLayoutParams(params);

            boolean keyboardVisible = imeInsets.bottom > 0;
            // 입력한 내용이 없을 때만 닫는다. 내용이 있으면 입력창과 함께 그대로 유지한다.
            if (keyboardWasVisible && !keyboardVisible
                    && inputContainer.getVisibility() == View.VISIBLE && !hasUnsavedInput()) {
                discardInput();
            }
            keyboardWasVisible = keyboardVisible;
            return insets;
        });
    }

    @Override
    public void onDestroyView() {
        draftSaver.cancel();
        saveDraftNow();
        // 입력창을 연 채로 드로어 등으로 이동해도 다른 화면에서 하단 NavBar가 사라지지 않게 한다.
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

