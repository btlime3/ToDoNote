package kr.akotis.todonote.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.NoteAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.NoteItem;

public class NoteFragment extends Fragment {

    private static final int NEW_NOTE_DRAFT_NOTICE_MS = 8000;

    private RecyclerView recyclerNote;
    private TextInputEditText etSearch;
    private FloatingActionButton fabNote;
    private DatabaseHelper dbHelper;
    private NoteAdapter noteAdapter;
    private List<NoteItem> allNotes;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_note, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        recyclerNote = view.findViewById(R.id.recycler_note);
        etSearch = view.findViewById(R.id.et_search);
        fabNote = view.findViewById(R.id.fab_note);

        // RecyclerView 설정 (가로 3개씩)
        noteAdapter = new NoteAdapter();
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        recyclerNote.setLayoutManager(layoutManager);
        recyclerNote.setAdapter(noteAdapter);

        // 노트 클릭 리스너
        noteAdapter.setOnNoteClickListener(note -> {
            if (note.isLocked()) {
                // 잠금된 노트: 암호 입력 다이얼로그 표시
                showPasswordDialog(note);
            } else {
                // 잠금 해제된 노트: 바로 편집 화면으로 이동
                NoteEditFragment editFragment = NoteEditFragment.newInstance(note.getId());
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, editFragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        // 검색 기능
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterNotes(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // FAB 클릭 - 새 노트 작성
        fabNote.setOnClickListener(v -> {
            NoteEditFragment editFragment = NoteEditFragment.newInstance();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, editFragment)
                    .addToBackStack(null)
                    .commit();
        });

        // 드래그 앤 드롭으로 순서 변경 설정
        setupDragAndDrop();

        // 노트 목록 로드
        loadNotes();

        return view;
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                noteAdapter.moveItem(fromPosition, toPosition);
                
                // 순서 변경 후 데이터베이스 업데이트
                dbHelper.updateNoteOrder(noteAdapter.getNoteList());
                
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 스와이프 기능 없음
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerNote);
    }

    private void loadNotes() {
        allNotes = dbHelper.getAllNotes();
        noteAdapter.setNotes(allNotes);
    }

    private void filterNotes(String query) {
        if (query == null || query.trim().isEmpty()) {
            noteAdapter.setNotes(allNotes);
            return;
        }

        query = query.toLowerCase().trim();
        List<NoteItem> filtered = new ArrayList<>();

        for (NoteItem note : allNotes) {
            String title = note.getTitle() != null ? note.getTitle().toLowerCase() : "";
            String content = note.getContent() != null ? note.getContent().toLowerCase() : "";

            if (title.contains(query) || content.contains(query)) {
                filtered.add(note);
            }
        }

        noteAdapter.setNotes(filtered);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotes();
        offerNewNoteDraft();
    }

    /** 작성하다 나간 새 노트는 목록에 없으므로 여기서 이어 쓸 수 있게 안내한다. */
    private void offerNewNoteDraft() {
        if (dbHelper.getNoteDraft(DatabaseHelper.DRAFT_NEW_ITEM) == null) {
            return;
        }
        Snackbar.make(recyclerNote, "작성 중이던 새 노트가 있습니다.", NEW_NOTE_DRAFT_NOTICE_MS)
                .setAnchorView(fabNote)
                .setAction("이어 쓰기", v -> requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, NoteEditFragment.newInstanceRestoringDraft())
                        .addToBackStack(null)
                        .commit())
                .show();
    }

    private void showPasswordDialog(NoteItem note) {
        // 암호가 설정되어 있는지 확인
        if (!dbHelper.hasNotePassword()) {
            // 암호가 설정되지 않은 경우: 암호 설정 안내
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("암호 미설정")
                    .setMessage("노트 암호가 설정되지 않았습니다. 왼쪽 메뉴에서 '노트 암호 설정'을 통해 암호를 설정해주세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        
        if (canUseBiometric) {
            // 지문 인증이 가능하면 먼저 지문 인증 시도
            authenticateWithBiometric(note.getId(), true);
        } else {
            // 지문 인증이 불가능하면 암호 입력 다이얼로그 표시
            showPasswordInputDialog(note.getId());
        }
    }

    private void showPasswordInputDialog(long noteId) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        // 힌트 표시
        String hint = dbHelper.getNotePasswordHint();
        String message = null;
        if (hint != null && !hint.isEmpty()) {
            message = "힌트: " + hint;
        }

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

        if (!canUseBiometric) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("잠금 해제")
                .setMessage(message)
                .setView(dialogView)
                .setPositiveButton("확인", (d, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(), "암호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (dbHelper.verifyNotePassword(password)) {
                        // 암호가 맞으면 편집 화면으로 이동
                        openNoteEditor(noteId);
                    } else {
                        Toast.makeText(requireContext(), "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .create();

        // 지문 인증 버튼 클릭
        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateWithBiometric(noteId, false);
        });

        dialog.show();
    }

    private void openNoteEditor(long noteId) {
        NoteEditFragment editFragment = NoteEditFragment.newInstance(noteId);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editFragment)
                .addToBackStack(null)
                .commit();
    }

    private void authenticateWithBiometric(long noteId, boolean showPasswordOnCancel) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("지문 인증")
                .setSubtitle("지문을 인식하여 노트 잠금을 해제하세요")
                .setNegativeButtonText(showPasswordOnCancel ? "암호 입력" : "취소")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        // 지문 인증 성공 시 노트 편집 화면으로 이동
                        openNoteEditor(noteId);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            // 사용자가 취소하거나 "암호 입력" 버튼을 누른 경우
                            if (showPasswordOnCancel) {
                                showPasswordInputDialog(noteId);
                            }
                        } else {
                            Toast.makeText(requireContext(), "지문 인증 실패: " + errString, Toast.LENGTH_SHORT).show();
                            if (showPasswordOnCancel) {
                                // 오류 발생 시에도 암호 입력 다이얼로그 표시
                                showPasswordInputDialog(noteId);
                            }
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(requireContext(), "지문 인증에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

