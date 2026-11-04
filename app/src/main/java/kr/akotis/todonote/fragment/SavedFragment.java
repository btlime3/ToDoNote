package kr.akotis.todonote.fragment;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.TodayFeedAdapter;
import kr.akotis.todonote.adapter.TodaySwipeCallback;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.model.TodayItem;

/** 나중에 보려고 저장한 소식. 메인 DB에 있으므로 오늘 화면의 소식이 지워져도 남고, 내보내기에도 포함된다. */
public class SavedFragment extends Fragment {
    private DatabaseHelper dbHelper;
    private TodayFeedAdapter adapter;
    private TextView tvEmpty;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved, container, false);
        dbHelper = new DatabaseHelper(requireContext());
        tvEmpty = view.findViewById(R.id.tv_saved_empty);

        adapter = new TodayFeedAdapter((item, position) -> openItem(item), (item, position) -> remove(item));
        adapter.setHighlightNew(false);
        RecyclerView recycler = view.findViewById(R.id.recycler_saved);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        new ItemTouchHelper(new TodaySwipeCallback(adapter,
                ContextCompat.getColor(requireContext(), R.color.color_delete),
                item -> "보관 해제",
                (item, position) -> remove(item))).attachToRecyclerView(recycler);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadItems();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    private void loadItems() {
        List<TodayItem> items = dbHelper.getSavedArticles();
        adapter.setPlainItems(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void remove(TodayItem item) {
        dbHelper.removeSavedArticle(item.getUrl());
        loadItems();
        // 저장한 소식은 다시 받을 수 없을 수 있으므로 실수로 지웠을 때 되돌릴 수 있게 한다.
        Snackbar.make(requireView(), "보관함에서 뺐습니다.", Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_nav))
                .setAction("취소", v -> {
                    if (isAdded()) {
                        dbHelper.saveArticle(item, item.getReceivedAt());
                        loadItems();
                    }
                })
                .show();
    }

    private void openItem(TodayItem item) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}
