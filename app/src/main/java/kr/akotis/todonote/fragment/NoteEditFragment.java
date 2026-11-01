package kr.akotis.todonote.fragment;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import kr.akotis.todonote.R;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.DraftItem;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.util.DebouncedRunner;

public class NoteEditFragment extends Fragment {

    private static final long DRAFT_SAVE_DELAY_MS = 1500;
    private static final String ARG_NOTE_ID = "note_id";
    private static final String ARG_RESTORE_DRAFT = "restore_draft";
    private static final String STATE_DRAFT_DECISION_PENDING = "draft_decision_pending";

    private EditText etTitle;
    private EditText etContent;
    private MaterialButton btnSave;
    private ImageButton btnCancel;
    private MaterialButton btnDelete;
    private MaterialButton btnLock;
    private DatabaseHelper dbHelper;
    private NoteItem editingNote;
    private long noteId = -1;

    /** 마지막으로 저장된 제목과 내용. 이 값과 다르면 저장하지 않은 변경이 있는 것이다. */
    private String savedTitle = "";
    private String savedContent = "";
    private DebouncedRunner draftSaver;
    private OnBackPressedCallback backCallback;
    private AlertDialog draftDialog;
    /** 복원 여부를 고르기 전에는 남아 있는 임시 저장본을 덮어쓰거나 지우면 안 된다. */
    private boolean draftDecisionPending;
    /** 저장, 버리기, 삭제로 화면을 나가는 중이면 더 이상 임시 저장하지 않는다. */
    private boolean exiting;

    public static NoteEditFragment newInstance(long noteId) {
        NoteEditFragment fragment = new NoteEditFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_NOTE_ID, noteId);
        fragment.setArguments(args);
        return fragment;
    }

    public static NoteEditFragment newInstance() {
        return new NoteEditFragment();
    }

    /** 작성하다 나간 새 노트를 확인 절차 없이 바로 이어서 편집한다. */
    public static NoteEditFragment newInstanceRestoringDraft() {
        NoteEditFragment fragment = new NoteEditFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_RESTORE_DRAFT, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            noteId = getArguments().getLong(ARG_NOTE_ID, -1);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_note_edit, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        draftSaver = new DebouncedRunner(DRAFT_SAVE_DELAY_MS, this::saveDraftNow);
        etTitle = view.findViewById(R.id.et_note_title);
        etContent = view.findViewById(R.id.et_note_content);
        btnSave = view.findViewById(R.id.btn_save);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnLock = view.findViewById(R.id.btn_lock);

        // note_id 확인
        if (noteId != -1) {
            // 편집 모드
            editingNote = dbHelper.getNoteById(noteId);
            if (editingNote != null) {
                savedTitle = trimmed(editingNote.getTitle());
                savedContent = trimmed(editingNote.getContent());
                etTitle.setText(editingNote.getTitle());
                etContent.setText(editingNote.getContent());
                // 삭제 버튼 표시
                btnDelete.setVisibility(View.VISIBLE);
                // 잠금 버튼 표시 및 상태 설정
                if (btnLock != null) {
                    btnLock.setVisibility(View.VISIBLE);
                    updateLockButton();
                }
            }
        } else {
            // 새 노트 작성 모드: 잠금 버튼 숨기기
            if (btnLock != null) {
                btnLock.setVisibility(View.GONE);
            }
        }

        // 저장 버튼 클릭
        btnSave.setOnClickListener(v -> saveNote());

        // 닫기 버튼 클릭
        btnCancel.setOnClickListener(v -> requestExit());

        // 삭제 버튼 클릭
        btnDelete.setOnClickListener(v -> showDeleteConfirmDialog());

        // 잠금 버튼 클릭
        if (btnLock != null) {
            btnLock.setOnClickListener(v -> toggleLock());
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setBottomNavVisible(false);

        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestExit();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        TextWatcher draftWatcher = new TextWatcher() {
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
        };
        etTitle.addTextChangedListener(draftWatcher);
        etContent.addTextChangedListener(draftWatcher);

        // 화면 회전처럼 편집이 이어지는 경우에는 입력란이 스스로 복원되므로 다시 묻지 않는다.
        boolean decisionWasPending = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_DRAFT_DECISION_PENDING);
        if (savedInstanceState == null || decisionWasPending) {
            offerDraftRestore();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DRAFT_DECISION_PENDING, draftDecisionPending);
    }

    @Override
    public void onPause() {
        super.onPause();
        // 다른 앱으로 전환한 뒤 시스템이 앱을 종료해도 내용이 남도록 바로 기록한다.
        draftSaver.cancel();
        saveDraftNow();
    }

    /** 저장된 내용과 입력 중인 내용이 다른 동안만 임시 저장본을 유지한다. */
    private void saveDraftNow() {
        if (exiting || draftDecisionPending || dbHelper == null || etTitle == null) {
            return;
        }
        long targetId = draftTargetId();
        if (hasUnsavedChanges()) {
            dbHelper.saveNoteDraft(targetId, etTitle.getText().toString(), etContent.getText().toString());
        } else {
            dbHelper.deleteNoteDraft(targetId);
        }
    }

    private void offerDraftRestore() {
        long targetId = draftTargetId();
        DraftItem draft = dbHelper.getNoteDraft(targetId);
        if (draft == null) {
            return;
        }
        if (trimmed(draft.getTitle()).equals(savedTitle) && trimmed(draft.getContent()).equals(savedContent)) {
            dbHelper.deleteNoteDraft(targetId);
            return;
        }
        if (getArguments() != null && getArguments().getBoolean(ARG_RESTORE_DRAFT)) {
            etTitle.setText(draft.getTitle());
            etContent.setText(draft.getContent());
            return;
        }

        draftDecisionPending = true;
        String savedAt = new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN).format(new Date(draft.getSavedAt()));
        draftDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("임시 저장된 내용")
                .setMessage(savedAt + "에 저장하지 않고 나간 내용이 있습니다.\n이어서 편집하시겠습니까?")
                .setCancelable(false)
                .setPositiveButton("복원", (dialog, which) -> {
                    draftDecisionPending = false;
                    etTitle.setText(draft.getTitle());
                    etContent.setText(draft.getContent());
                })
                .setNegativeButton("버리기", (dialog, which) -> {
                    draftDecisionPending = false;
                    dbHelper.deleteNoteDraft(targetId);
                })
                .show();
    }

    private long draftTargetId() {
        return editingNote != null ? noteId : DatabaseHelper.DRAFT_NEW_ITEM;
    }

    private boolean hasUnsavedChanges() {
        return !etTitle.getText().toString().trim().equals(savedTitle)
                || !etContent.getText().toString().trim().equals(savedContent);
    }

    private void requestExit() {
        if (!hasUnsavedChanges()) {
            discardAndExit();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("저장하지 않은 내용")
                .setMessage("변경한 내용을 저장하시겠습니까?")
                .setPositiveButton("저장", (dialog, which) -> saveNote())
                .setNegativeButton("버리기", (dialog, which) -> discardAndExit())
                .setNeutralButton("계속 편집", null)
                .show();
    }

    private void discardAndExit() {
        dbHelper.deleteNoteDraft(draftTargetId());
        exitEditor();
    }

    private void exitEditor() {
        exiting = true;
        draftSaver.cancel();
        backCallback.setEnabled(false);
        hideKeyboard();
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private void hideKeyboard() {
        View view = getView();
        if (view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setBottomNavVisible(boolean visible) {
        View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateLockButton() {
        if (editingNote != null && btnLock != null) {
            if (editingNote.isLocked()) {
                btnLock.setText("해제");
            } else {
                btnLock.setText("잠금");
            }
        }
    }

    private void toggleLock() {
        if (editingNote == null || noteId == -1) {
            return;
        }

        // 잠금 상태 변경 전에 현재 편집 중인 내용을 먼저 저장
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";

        editingNote.setTitle(title);
        editingNote.setContent(content);

        // 현재 내용을 먼저 저장 (노트 내용은 항상 평문으로 저장)
        int saveResult = dbHelper.updateNote(editingNote);
        if (saveResult <= 0) {
            Toast.makeText(requireContext(), "내용 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        savedTitle = title;
        savedContent = content;
        dbHelper.deleteNoteDraft(noteId);

        // 잠금 상태만 변경 (암호화 없이 접근 인증만 담당)
        boolean newLockState = !editingNote.isLocked();
        int result = dbHelper.toggleNoteLock(noteId, newLockState);
        if (result > 0) {
            editingNote.setLocked(newLockState);
            updateLockButton();
            String message = newLockState ? "노트가 잠금되었습니다." : "노트 잠금이 해제되었습니다.";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "잠금 상태 변경에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveNote() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(requireContext(), "제목 또는 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean saved;
        if (noteId != -1 && editingNote != null) {
            // 수정 모드
            editingNote.setTitle(title);
            editingNote.setContent(content);
            saved = dbHelper.updateNote(editingNote) > 0;
            if (!saved) {
                Toast.makeText(requireContext(), "수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 새 노트 추가
            saved = dbHelper.insertNote(new NoteItem(title, content)) > 0;
            if (!saved) {
                Toast.makeText(requireContext(), "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }

        if (saved) {
            dbHelper.deleteNoteDraft(draftTargetId());
            exitEditor();
        }
    }

    private void showDeleteConfirmDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("노트 삭제")
                .setMessage("이 노트를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> deleteNote())
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteNote() {
        if (noteId != -1) {
            int result = dbHelper.deleteNote(noteId);
            if (result > 0) {
                exitEditor();
            } else {
                Toast.makeText(requireContext(), "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String trimmed(String text) {
        return text != null ? text.trim() : "";
    }

    @Override
    public void onDestroyView() {
        draftSaver.cancel();
        if (!exiting && !draftDecisionPending && hasUnsavedChanges()) {
            saveDraftNow();
            // 탭이나 드로어로 이동해 화면이 사라지는 경우에만 안내한다. 화면 회전은 제외된다.
            if (isRemoving()) {
                Toast.makeText(requireContext(), "작성 중인 노트를 임시 저장했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
        if (draftDialog != null) {
            draftDialog.dismiss();
            draftDialog = null;
        }
        setBottomNavVisible(true);
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
