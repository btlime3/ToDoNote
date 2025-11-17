package kr.akotis.todonote;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.NoteItem;

public class NoteEditActivity extends AppCompatActivity {

    private TextInputEditText etTitle;
    private TextInputEditText etContent;
    private MaterialButton btnSave;
    private MaterialButton btnCancel;
    private MaterialButton btnDelete;
    private DatabaseHelper dbHelper;
    private NoteItem editingNote;
    private long noteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("노트 작성");
        }

        dbHelper = new DatabaseHelper(this);
        etTitle = findViewById(R.id.et_note_title);
        etContent = findViewById(R.id.et_note_content);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        btnDelete = findViewById(R.id.btn_delete);

        // Intent에서 note_id 확인
        noteId = getIntent().getLongExtra("note_id", -1);
        if (noteId != -1) {
            // 편집 모드
            editingNote = dbHelper.getNoteById(noteId);
            if (editingNote != null) {
                etTitle.setText(editingNote.getTitle());
                etContent.setText(editingNote.getContent());
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("노트 편집");
                }
                // 삭제 버튼 표시
                btnDelete.setVisibility(View.VISIBLE);
            }
        }

        // 저장 버튼 클릭
        btnSave.setOnClickListener(v -> saveNote());

        // 취소 버튼 클릭
        btnCancel.setOnClickListener(v -> finish());

        // 삭제 버튼 클릭
        btnDelete.setOnClickListener(v -> showDeleteConfirmDialog());
    }

    private void saveNote() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "제목 또는 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (noteId != -1 && editingNote != null) {
            // 수정 모드
            editingNote.setTitle(title);
            editingNote.setContent(content);
            int result = dbHelper.updateNote(editingNote);
            if (result > 0) {
                Toast.makeText(this, "노트가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 새 노트 추가
            NoteItem newNote = new NoteItem(title, content);
            long id = dbHelper.insertNote(newNote);
            if (id > 0) {
                Toast.makeText(this, "노트가 저장되었습니다.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showDeleteConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
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
                Toast.makeText(this, "노트가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

