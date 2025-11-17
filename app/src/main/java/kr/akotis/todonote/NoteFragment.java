package kr.akotis.todonote;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import kr.akotis.todonote.adapter.NoteAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.NoteItem;

public class NoteFragment extends Fragment {

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
            NoteEditFragment editFragment = NoteEditFragment.newInstance(note.getId());
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, editFragment)
                    .addToBackStack(null)
                    .commit();
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

        // 노트 목록 로드
        loadNotes();

        return view;
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
