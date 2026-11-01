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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.activity.MainActivity;
import kr.akotis.todonote.adapter.SearchResultAdapter;
import kr.akotis.todonote.adapter.SearchResultAdapter.Item;
import kr.akotis.todonote.adapter.SearchResultAdapter.Type;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.MessageItem;
import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.model.ToDoItem;
import kr.akotis.todonote.util.DebouncedRunner;
import kr.akotis.todonote.util.NoteUnlocker;
import kr.akotis.todonote.util.SearchQuery;

/** 메모, 노트, 알림, 할 일을 한 번에 찾는 화면. 휴지통 항목은 찾지 않는다. */
public class SearchFragment extends Fragment {

    private static final long SEARCH_DELAY_MS = 150;

    private DatabaseHelper dbHelper;
    private EditText etSearch;
    private TextView tvStatus;
    private SearchResultAdapter adapter;
    private DebouncedRunner searchRunner;
    private List<Item> searchTargets = new ArrayList<>();
    private boolean keyboardRequested;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);
        dbHelper = new DatabaseHelper(requireContext());
        etSearch = view.findViewById(R.id.et_search);
        tvStatus = view.findViewById(R.id.tv_search_status);
        searchRunner = new DebouncedRunner(SEARCH_DELAY_MS, this::runSearch);

        adapter = new SearchResultAdapter(this::openResult);
        RecyclerView recycler = view.findViewById(R.id.recycler_search);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchRunner.schedule();
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 결과에서 항목을 열어 수정하고 돌아올 수 있으므로 매번 다시 읽는다.
        searchTargets = loadSearchTargets();
        runSearch();
        if (!keyboardRequested) {
            keyboardRequested = true;
            etSearch.requestFocus();
            etSearch.post(() -> {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private List<Item> loadSearchTargets() {
        List<Item> targets = new ArrayList<>();
        for (MemoItem memo : dbHelper.getAllMemos()) {
            targets.add(new Item(Type.MEMO, memo.getId(), null, memo.getContent(), memo.getUpdatedAt(), false, false));
        }
        for (NoteItem note : dbHelper.getAllNotes()) {
            targets.add(new Item(Type.NOTE, note.getId(), note.getTitle(), note.getContent(), note.getUpdatedAt(),
                    note.isLocked(), false));
        }
        for (MessageItem message : dbHelper.getAllMessages()) {
            // 알림 본문은 HTML이므로 태그를 빼고 찾는다.
            String body = message.getBody() != null
                    ? HtmlCompat.fromHtml(message.getBody(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString() : "";
            targets.add(new Item(Type.MESSAGE, message.getId(), message.getTitle(), body, message.getReceivedAt(),
                    false, false));
        }
        for (ToDoItem todo : dbHelper.getAllToDos()) {
            targets.add(new Item(Type.TODO, todo.getId(), todo.getTitle(), todo.getDetail(), todo.getDate(),
                    false, todo.isCompleted()));
        }
        return targets;
    }

    private void runSearch() {
        SearchQuery query = SearchQuery.parse(etSearch.getText().toString());
        List<Item> results = new ArrayList<>();
        if (!query.isEmpty()) {
            for (Item item : searchTargets) {
                if (item.matches(query)) {
                    results.add(item);
                }
            }
        }
        adapter.setResults(results, query);
        if (query.isEmpty()) {
            tvStatus.setText("메모, 노트, 알림, 할 일에서 찾습니다.");
        } else if (results.isEmpty()) {
            tvStatus.setText("검색 결과가 없습니다.");
        } else {
            tvStatus.setText("검색 결과 " + results.size() + "개");
        }
    }

    private void openResult(Item item) {
        MainActivity activity = (MainActivity) requireActivity();
        switch (item.type) {
            case MEMO:
                activity.showMemo(item.id);
                break;
            case NOTE:
                if (item.locked) {
                    NoteUnlocker.unlock(this, dbHelper, () -> openNote(item.id));
                } else {
                    openNote(item.id);
                }
                break;
            case MESSAGE:
                activity.showMessages();
                break;
            case TODO:
                activity.showToDos(item.completed);
                break;
        }
    }

    private void openNote(long noteId) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, NoteEditFragment.newInstance(noteId))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        searchRunner.cancel();
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
