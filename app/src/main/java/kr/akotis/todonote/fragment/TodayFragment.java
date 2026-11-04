package kr.akotis.todonote.fragment;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.TodayFeedAdapter;
import kr.akotis.todonote.adapter.TodaySwipeCallback;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.database.TodayFeedDatabase;
import kr.akotis.todonote.model.TodayItem;
import kr.akotis.todonote.service.MyFirebaseMessagingService;

/** 서버가 보낸 소식을 오늘 소식과 새로운 기술 두 탭으로 나누어 보여 준다. */
public class TodayFragment extends Fragment {
    private static final String PREFS = "today_screen";
    private static final String KEY_TAB = "tab";
    private static final int TAB_NEWS = 0;
    private static final int TAB_TECH = 1;

    private TodayFeedDatabase database;
    private DatabaseHelper dbHelper;
    private Set<String> savedUrls;
    private TabLayout tabs;
    private RecyclerView recyclerNews;
    private RecyclerView recyclerTech;
    private TodayFeedAdapter newsAdapter;
    private TodayFeedAdapter techAdapter;
    private TextView tvEmpty;
    private boolean newsEmpty;
    private boolean techEmpty;

    /** 화면을 보고 있는 동안 도착한 소식을 바로 반영한다. */
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadItems();
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_today, container, false);
        database = TodayFeedDatabase.getInstance(requireContext());
        dbHelper = new DatabaseHelper(requireContext());
        savedUrls = dbHelper.getSavedArticleUrls();
        tvEmpty = view.findViewById(R.id.tv_today_empty);
        tabs = view.findViewById(R.id.tabs_today);

        // 탭마다 목록을 따로 두어 다른 탭을 보고 돌아와도 읽던 위치가 그대로 있다.
        recyclerNews = view.findViewById(R.id.recycler_today_news);
        recyclerTech = view.findViewById(R.id.recycler_today_tech);
        newsAdapter = new TodayFeedAdapter((item, position) -> openItem(item, newsAdapter, position),
                (item, position) -> toggleSaved(item));
        techAdapter = new TodayFeedAdapter((item, position) -> openItem(item, techAdapter, position),
                (item, position) -> toggleSaved(item));
        setupList(recyclerNews, newsAdapter);
        setupList(recyclerTech, techAdapter);

        setupTabs();
        return view;
    }

    private void setupList(RecyclerView recycler, TodayFeedAdapter adapter) {
        adapter.setSavedUrls(savedUrls);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        new ItemTouchHelper(new TodaySwipeCallback(adapter,
                ContextCompat.getColor(requireContext(), R.color.color_primary_text),
                item -> adapter.isSaved(item) ? "보관 해제" : "보관",
                (item, position) -> {
                    // 카드는 목록에 그대로 남으므로 밀린 자리를 되돌린다.
                    adapter.notifyItemChanged(position);
                    toggleSaved(item);
                })).attachToRecyclerView(recycler);
    }

    private void toggleSaved(TodayItem item) {
        boolean save = !savedUrls.contains(item.getUrl());
        setSaved(item, save);
        Snackbar.make(requireView(), save ? "보관함에 저장했습니다." : "보관함에서 뺐습니다.", Snackbar.LENGTH_SHORT)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_nav))
                .setAction("취소", v -> {
                    if (isAdded()) {
                        setSaved(item, !save);
                    }
                })
                .show();
    }

    private void setSaved(TodayItem item, boolean save) {
        if (save) {
            dbHelper.saveArticle(item, System.currentTimeMillis());
            savedUrls.add(item.getUrl());
        } else {
            dbHelper.removeSavedArticle(item.getUrl());
            savedUrls.remove(item.getUrl());
        }
        newsAdapter.refreshItem(item.getUrl());
        techAdapter.refreshItem(item.getUrl());
    }

    private void setupTabs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        TabLayout.Tab saved = tabs.getTabAt(prefs.getInt(KEY_TAB, TAB_NEWS));
        if (saved != null) {
            saved.select();
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                prefs.edit().putInt(KEY_TAB, tab.getPosition()).apply();
                showSelectedTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                (tab.getPosition() == TAB_TECH ? recyclerTech : recyclerNews).smoothScrollToPosition(0);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(requireContext(), updateReceiver,
                new IntentFilter(MyFirebaseMessagingService.ACTION_TODAY_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        requireContext().unregisterReceiver(updateReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    private void loadItems() {
        // 보관함에서 뺀 글이 다시 표시되지 않도록 돌아올 때마다 읽는다. 어댑터가 같은 집합을 참조한다.
        savedUrls.clear();
        savedUrls.addAll(dbHelper.getSavedArticleUrls());
        long lastReceivedAt = database.getLastReceivedAt();
        String lastReceived = lastReceivedAt > 0
                ? new SimpleDateFormat("M월 d일 a h:mm 수신", Locale.KOREAN).format(new Date(lastReceivedAt))
                : "";
        List<TodayItem> news = database.getItems(TodayItem.CATEGORY_NEWS);
        List<TodayItem> tech = database.getItems(TodayItem.CATEGORY_TECH);
        newsAdapter.setItems(news, lastReceived);
        techAdapter.setItems(tech, lastReceived);
        newsEmpty = news.isEmpty();
        techEmpty = tech.isEmpty();
        showSelectedTab();
    }

    private void showSelectedTab() {
        boolean techSelected = tabs.getSelectedTabPosition() == TAB_TECH;
        recyclerNews.setVisibility(techSelected ? View.GONE : View.VISIBLE);
        recyclerTech.setVisibility(techSelected ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility((techSelected ? techEmpty : newsEmpty) ? View.VISIBLE : View.GONE);
    }

    private void openItem(TodayItem item, TodayFeedAdapter adapter, int position) {
        if (!item.isRead()) {
            database.markRead(item.getId());
            item.setRead(true);
            adapter.notifyItemChanged(position);
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}
