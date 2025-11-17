package kr.akotis.todonote;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.NoteItem;

public class NoteEditFragment extends Fragment {

    private TextInputEditText etTitle;
    private TextInputEditText etContent;
    private MaterialButton btnSave;
    private MaterialButton btnCancel;
    private MaterialButton btnDelete;
    private MaterialButton btnLock;
    private DatabaseHelper dbHelper;
    private NoteItem editingNote;
    private long noteId = -1;

    public static NoteEditFragment newInstance(long noteId) {
        NoteEditFragment fragment = new NoteEditFragment();
        Bundle args = new Bundle();
        args.putLong("note_id", noteId);
        fragment.setArguments(args);
        return fragment;
    }

    public static NoteEditFragment newInstance() {
        return new NoteEditFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            noteId = getArguments().getLong("note_id", -1);
        }
    }

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_note_edit, container, false);

        dbHelper = new DatabaseHelper(requireContext());
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

        // 취소 버튼 클릭
        btnCancel.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        // 삭제 버튼 클릭
        btnDelete.setOnClickListener(v -> showDeleteConfirmDialog());

        // 잠금 버튼 클릭
        if (btnLock != null) {
            btnLock.setOnClickListener(v -> toggleLock());
        }

        return view;
    }

    private void updateLockButton() {
        if (editingNote != null && btnLock != null) {
            if (editingNote.isLocked()) {
                btnLock.setText("잠금 해제");
            } else {
                btnLock.setText("잠금");
            }
        }
    }

    private void toggleLock() {
        if (editingNote == null || noteId == -1) {
            return;
        }

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

        if (noteId != -1 && editingNote != null) {
            // 수정 모드
            editingNote.setTitle(title);
            editingNote.setContent(content);
            int result = dbHelper.updateNote(editingNote);
            if (result > 0) {
                Toast.makeText(requireContext(), "노트가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            } else {
                Toast.makeText(requireContext(), "수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 새 노트 추가
            NoteItem newNote = new NoteItem(title, content);
            long id = dbHelper.insertNote(newNote);
            if (id > 0) {
                Toast.makeText(requireContext(), "노트가 저장되었습니다.", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            } else {
                Toast.makeText(requireContext(), "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showDeleteConfirmDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("노트 삭제")
                .setMessage("이 노트를 영구적으로 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    deleteNote();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteNote() {
        if (noteId != -1) {
            int result = dbHelper.deleteNote(noteId);
            if (result > 0) {
                Toast.makeText(requireContext(), "노트가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            } else {
                Toast.makeText(requireContext(), "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

