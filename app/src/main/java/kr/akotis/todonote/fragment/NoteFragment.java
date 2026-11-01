package kr.akotis.todonote.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.NoteAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.util.NoteUnlocker;
import kr.akotis.todonote.util.SearchQuery;

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
        etSearch.setHint("노트 검색...");
        fabNote = view.findViewById(R.id.fab_note);

        // RecyclerView 설정 (가로 3개씩)
        noteAdapter = new NoteAdapter();
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        recyclerNote.setLayoutManager(layoutManager);
        recyclerNote.setAdapter(noteAdapter);

        // 노트 클릭 리스너
        noteAdapter.setOnNoteClickListener(note -> {
            if (note.isLocked()) {
                // 잠금된 노트: 지문 또는 암호로 인증한 뒤 열기
                NoteUnlocker.unlock(this, dbHelper, () -> openNoteEditor(note.getId()));
            } else {
                // 잠금 해제된 노트: 바로 편집 화면으로 이동
                openNoteEditor(note.getId());
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
            private boolean orderChanged;

            @Override
            public boolean isLongPressDragEnabled() {
                // 검색 결과는 일부만 보이므로 순서를 바꾸면 전체 순서가 어긋난다.
                return SearchQuery.parse(etSearch.getText() != null ? etSearch.getText().toString() : "").isEmpty();
            }

            @Override
            public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
                // 고정한 노트와 고정하지 않은 노트는 서로 섞이지 않는다.
                NoteItem from = noteAdapter.getNoteAt(current.getBindingAdapterPosition());
                NoteItem to = noteAdapter.getNoteAt(target.getBindingAdapterPosition());
                return from != null && to != null && from.isPinned() == to.isPinned();
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                noteAdapter.moveItem(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                orderChanged = true;
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 끌어다 놓은 뒤 한 번만 저장한다.
                if (orderChanged) {
                    orderChanged = false;
                    dbHelper.updateNoteOrder(noteAdapter.getNoteList());
                }
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
        // 검색 중에 돌아와도 검색 결과를 유지한다.
        filterNotes(etSearch.getText() != null ? etSearch.getText().toString() : "");
    }

    private void filterNotes(String rawQuery) {
        SearchQuery query = SearchQuery.parse(rawQuery);
        if (query.isEmpty()) {
            noteAdapter.setNotes(allNotes);
            return;
        }

        List<NoteItem> filtered = new ArrayList<>();
        for (NoteItem note : allNotes) {
            // 잠긴 노트는 내용으로 찾히지 않아야 검색으로 내용을 짐작할 수 없다.
            boolean matches = note.isLocked()
                    ? query.matches(note.getTitle())
                    : query.matches(note.getTitle(), note.getContent());
            if (matches) {
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

    private void openNoteEditor(long noteId) {
        NoteEditFragment editFragment = NoteEditFragment.newInstance(noteId);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}

