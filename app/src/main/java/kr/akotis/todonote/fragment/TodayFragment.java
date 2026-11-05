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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.TodayFeedAdapter;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.database.TodayFeedDatabase;
import kr.akotis.todonote.model.TodayItem;
import kr.akotis.todonote.service.MyFirebaseMessagingService;

/** 서버가 보낸 소식을 오늘 소식과 새로운 기술 두 탭으로 나누어 보여 준다. 좌우로 밀어 탭을 옮긴다. */
public class TodayFragment extends Fragment {
    private static final String PREFS = "today_screen";
    private static final String KEY_TAB = "tab";
    private static final int TAB_NEWS = 0;
    private static final int TAB_TECH = 1;
    private static final String[] TAB_TITLES = {"오늘 소식", "새로운 기술"};

    private TodayFeedDatabase database;
    private DatabaseHelper dbHelper;
    private Set<String> savedUrls;
    private TodayFeedAdapter newsAdapter;
    private TodayFeedAdapter techAdapter;
    private final RecyclerView[] recyclers = new RecyclerView[TAB_TITLES.length];
    private final TextView[] emptyViews = new TextView[TAB_TITLES.length];
    private final boolean[] emptyTabs = new boolean[TAB_TITLES.length];

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

        newsAdapter = new TodayFeedAdapter((item, position) -> openItem(item, newsAdapter, position),
                (item, position) -> toggleSaved(item));
        techAdapter = new TodayFeedAdapter((item, position) -> openItem(item, techAdapter, position),
                (item, position) -> toggleSaved(item));
        newsAdapter.setSavedUrls(savedUrls);
        techAdapter.setSavedUrls(savedUrls);

        setupPager(view.findViewById(R.id.pager_today), view.findViewById(R.id.tabs_today));
        return view;
    }

    private void setupPager(ViewPager2 pager, TabLayout tabs) {
        pager.setAdapter(new PageAdapter());
        // 두 탭을 모두 살려 두어 옆 탭을 보고 돌아와도 읽던 위치가 그대로 있다.
        pager.setOffscreenPageLimit(1);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        pager.setCurrentItem(prefs.getInt(KEY_TAB, TAB_NEWS), false);
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(TAB_TITLES[position])).attach();
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                prefs.edit().putInt(KEY_TAB, position).apply();
            }
        });
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                RecyclerView recycler = recyclers[tab.getPosition()];
                if (recycler != null) {
                    recycler.smoothScrollToPosition(0);
                }
            }
        });
    }

    /** 탭마다 목록 하나를 둔다. 뷰 타입을 탭 번호로 써서 페이지가 서로 바뀌어 쓰이지 않게 한다. */
    private final class PageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return TAB_TITLES.length;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int tab) {
            View page = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_today_page, parent, false);
            recyclers[tab] = page.findViewById(R.id.recycler_today);
            recyclers[tab].setLayoutManager(new LinearLayoutManager(parent.getContext()));
            recyclers[tab].setAdapter(tab == TAB_TECH ? techAdapter : newsAdapter);
            emptyViews[tab] = page.findViewById(R.id.tv_today_empty);
            return new RecyclerView.ViewHolder(page) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int tab) {
            showEmptyState(tab);
        }
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
        emptyTabs[TAB_NEWS] = news.isEmpty();
        emptyTabs[TAB_TECH] = tech.isEmpty();
        showEmptyState(TAB_NEWS);
        showEmptyState(TAB_TECH);
    }

    private void showEmptyState(int tab) {
        if (emptyViews[tab] != null) {
            emptyViews[tab].setVisibility(emptyTabs[tab] ? View.VISIBLE : View.GONE);
        }
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
