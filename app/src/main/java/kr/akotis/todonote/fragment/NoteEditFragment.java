package kr.akotis.todonote.fragment;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
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
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.R;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.DraftItem;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.model.NoteVersion;
import kr.akotis.todonote.util.DebouncedRunner;

public class NoteEditFragment extends Fragment {

    private static final long DRAFT_SAVE_DELAY_MS = 1500;
    private static final int VERSION_PREVIEW_LENGTH = 40;
    private static final String ARG_NOTE_ID = "note_id";
    private static final String ARG_RESTORE_DRAFT = "restore_draft";
    private static final String STATE_DRAFT_DECISION_PENDING = "draft_decision_pending";
    private static final String STATE_PROCESS_TOKEN = "process_token";
    /** 앱이 종료되었다가 복원된 것인지 화면 회전인지 구분한다. 프로세스마다 값이 다르다. */
    private static final long PROCESS_TOKEN = System.nanoTime();

    private EditText etTitle;
    private EditText etContent;
    private MaterialButton btnSave;
    private ImageButton btnCancel;
    private ImageButton btnDelete;
    private ImageButton btnLock;
    private ImageButton btnHistory;
    private DatabaseHelper dbHelper;
    private NoteItem editingNote;
    private long noteId = -1;

    /** 마지막으로 저장된 제목과 내용. 이 값과 다르면 저장하지 않은 변경이 있는 것이다. */
    private String savedTitle = "";
    private String savedContent = "";
    private DebouncedRunner draftSaver;
    private OnBackPressedCallback backCallback;
    private AlertDialog draftDialog;
    /** 편집 화면이 떠 있는 동안에만 의미가 있는 다이얼로그. 화면이 사라질 때 함께 닫는다. */
    private AlertDialog activeDialog;
    /** 복원 여부를 고르기 전에는 남아 있는 임시 저장본을 덮어쓰거나 지우면 안 된다. */
    private boolean draftDecisionPending;
    /** 저장, 버리기, 삭제로 화면을 나가는 중이면 더 이상 임시 저장하지 않는다. */
    private boolean exiting;
    /** 알림 화면 등을 열었다가 돌아오면 입력란이 스스로 복원되므로 임시 저장본을 다시 묻지 않는다. */
    private boolean draftChecked;

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
        btnHistory = view.findViewById(R.id.btn_history);

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
                btnLock.setVisibility(View.VISIBLE);
                updateLockButton();
                btnHistory.setVisibility(View.VISIBLE);
            }
        }

        // 저장 버튼 클릭
        btnSave.setOnClickListener(v -> saveNote());

        // 닫기 버튼 클릭
        btnCancel.setOnClickListener(v -> requestExit());

        // 삭제 버튼 클릭
        btnDelete.setOnClickListener(v -> showDeleteConfirmDialog());

        // 잠금 버튼 클릭
        btnLock.setOnClickListener(v -> toggleLock());

        // 이전 버전 버튼 클릭
        btnHistory.setOnClickListener(v -> showHistoryDialog());

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

        if (needsUnlockAfterRestore(savedInstanceState)) {
            // 앱이 종료된 뒤 복원된 잠긴 노트는 내용을 보이지 않고 목록으로 돌아가 다시 인증하게 한다.
            // 임시 저장본은 그대로 남아 인증 후 다시 열 때 복원할 수 있다.
            view.setVisibility(View.INVISIBLE);
            exitEditor();
            return;
        }

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
        boolean decisionWasPending = draftDecisionPending || (savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_DRAFT_DECISION_PENDING));
        if ((savedInstanceState == null && !draftChecked) || decisionWasPending) {
            offerDraftRestore();
        }
        draftChecked = true;
    }

    private boolean needsUnlockAfterRestore(@Nullable Bundle savedInstanceState) {
        return savedInstanceState != null
                && savedInstanceState.getLong(STATE_PROCESS_TOKEN, PROCESS_TOKEN) != PROCESS_TOKEN
                && editingNote != null
                && editingNote.isLocked();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DRAFT_DECISION_PENDING, draftDecisionPending);
        outState.putLong(STATE_PROCESS_TOKEN, PROCESS_TOKEN);
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
        try {
            if (hasUnsavedChanges()) {
                dbHelper.saveNoteDraft(targetId, etTitle.getText().toString(), etContent.getText().toString());
            } else {
                dbHelper.deleteNoteDraft(targetId);
            }
        } catch (SQLiteException e) {
            // 저장 공간 부족 등으로 실패해도 입력 중인 화면이 종료되면 안 된다.
            Log.w("NoteEditFragment", "Could not save note draft", e);
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
        activeDialog = new MaterialAlertDialogBuilder(requireContext())
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
        if (getView() == null) {
            return;
        }
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
        if (editingNote != null) {
            boolean locked = editingNote.isLocked();
            btnLock.setImageResource(locked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_opened);
            String label = locked ? "잠금 해제" : "잠금";
            btnLock.setContentDescription(label);
            btnLock.setTooltipText(label);
        }
    }

    private void showHistoryDialog() {
        List<NoteVersion> versions = dbHelper.getNoteHistory(noteId);
        if (versions.isEmpty()) {
            Toast.makeText(requireContext(), "이전 버전이 없습니다. 내용을 수정해 저장하면 여기에 쌓입니다.", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] rows = new CharSequence[versions.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = describeVersion(versions.get(i));
        }
        activeDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("이전 버전")
                .setItems(rows, (dialog, which) -> showVersionPreview(versions.get(which)))
                .setNegativeButton("닫기", null)
                .setNeutralButton("이력 삭제", (dialog, which) -> confirmDeleteHistory())
                .show();
    }

    private String describeVersion(NoteVersion version) {
        String content = version.getContent().trim();
        int lineEnd = content.indexOf('\n');
        String firstLine = lineEnd >= 0 ? content.substring(0, lineEnd) : content;
        if (firstLine.length() > VERSION_PREVIEW_LENGTH) {
            firstLine = firstLine.substring(0, VERSION_PREVIEW_LENGTH) + "…";
        }
        String summary = firstLine.isEmpty() ? "내용 없음" : firstLine;
        return formatVersionTime(version) + " · " + content.length() + "자\n" + summary;
    }

    private String formatVersionTime(NoteVersion version) {
        return new SimpleDateFormat("yyyy.M.d a h:mm", Locale.KOREAN).format(new Date(version.getSavedAt()));
    }

    private void showVersionPreview(NoteVersion version) {
        String title = version.getTitle().trim().isEmpty() ? "제목 없음" : version.getTitle();
        activeDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(formatVersionTime(version) + "에 저장한 내용\n\n" + version.getContent())
                .setPositiveButton("이 버전으로 되돌리기", (dialog, which) -> requestRestoreVersion(version))
                .setNegativeButton("목록", (dialog, which) -> showHistoryDialog())
                .show();
        // 일부만 복사해서 쓸 수 있도록 내용을 선택 가능하게 둔다.
        TextView message = activeDialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextIsSelectable(true);
        }
    }

    private void requestRestoreVersion(NoteVersion version) {
        if (!hasUnsavedChanges()) {
            restoreVersion(version);
            return;
        }
        activeDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("저장하지 않은 내용")
                .setMessage("지금 편집 중인 내용은 저장되어 있지 않습니다. 이 버전으로 바꾸면 사라집니다.")
                .setPositiveButton("되돌리기", (dialog, which) -> restoreVersion(version))
                .setNegativeButton("취소", null)
                .show();
    }

    /** 입력란에만 채운다. 저장해야 적용되고, 그때 현재 내용은 다시 이전 버전으로 보관된다. */
    private void restoreVersion(NoteVersion version) {
        etTitle.setText(version.getTitle());
        etContent.setText(version.getContent());
        Toast.makeText(requireContext(), "이전 버전을 불러왔습니다. 저장해야 적용됩니다.", Toast.LENGTH_LONG).show();
    }

    private void confirmDeleteHistory() {
        activeDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("이력 삭제")
                .setMessage("이 노트의 이전 버전을 모두 삭제하시겠습니까?\n현재 내용은 그대로 유지됩니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    dbHelper.deleteNoteHistory(noteId);
                    Toast.makeText(requireContext(), "이전 버전을 삭제했습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void toggleLock() {
        if (editingNote == null || noteId == -1) {
            return;
        }
        // 암호 없이 잠그면 인증할 방법이 없어 노트를 열 수 없게 된다.
        if (!editingNote.isLocked() && !dbHelper.hasNotePassword()) {
            Toast.makeText(requireContext(), "먼저 왼쪽 메뉴의 '암호 설정'에서 암호를 설정해주세요.", Toast.LENGTH_LONG).show();
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
        activeDialog = new MaterialAlertDialogBuilder(requireContext())
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
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
        setBottomNavVisible(true);
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
