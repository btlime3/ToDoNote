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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.R;
import kr.akotis.todonote.adapter.TodayFeedAdapter;
import kr.akotis.todonote.database.TodayFeedDatabase;
import kr.akotis.todonote.model.TodayItem;
import kr.akotis.todonote.service.MyFirebaseMessagingService;

/** 서버가 보낸 최신 오픈소스, 연구, AI, 보안, IT 소식을 보여 주는 화면. */
public class TodayFragment extends Fragment {
    private static final String PREFS = "today_screen";
    private static final String KEY_CATEGORY = "category";
    /** 전체 칩을 나타낸다. */
    private static final String CATEGORY_ALL = "all";
    /** 한글과 영문의 글자 높이가 달라 칩 높이를 고정한다. */
    private static final int CHIP_HEIGHT_DP = 36;
    private static final String[] CATEGORIES = {
            CATEGORY_ALL,
            TodayItem.CATEGORY_OPENSOURCE,
            TodayItem.CATEGORY_RESEARCH,
            TodayItem.CATEGORY_AI,
            TodayItem.CATEGORY_SECURITY,
            TodayItem.CATEGORY_NEWS,
    };

    private TodayFeedDatabase database;
    private TodayFeedAdapter adapter;
    private RecyclerView recycler;
    private TextView tvDate;
    private TextView tvLastReceived;
    private TextView tvEmpty;
    private String selectedCategory;

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
        tvDate = view.findViewById(R.id.tv_today_date);
        tvLastReceived = view.findViewById(R.id.tv_today_last_received);
        tvEmpty = view.findViewById(R.id.tv_today_empty);

        adapter = new TodayFeedAdapter(this::openItem);
        recycler = view.findViewById(R.id.recycler_today);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        setupCategoryChips(view.findViewById(R.id.chip_group_today));
        return view;
    }

    private void setupCategoryChips(ChipGroup group) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        selectedCategory = prefs.getString(KEY_CATEGORY, CATEGORY_ALL);
        boolean selectedExists = false;
        for (String category : CATEGORIES) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(CATEGORY_ALL.equals(category) ? "전체" : TodayItem.categoryLabel(category));
            chip.setTag(category);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(CHIP_HEIGHT_DP * getResources().getDisplayMetrics().density);
            // 시스템이 복원하는 값이 아니라 저장해 둔 분류를 기준으로 선택한다.
            chip.setSaveEnabled(false);
            group.addView(chip);
            if (category.equals(selectedCategory)) {
                group.check(chip.getId());
                selectedExists = true;
            }
        }
        if (!selectedExists) {
            selectedCategory = CATEGORY_ALL;
            group.check(group.getChildAt(0).getId());
        }
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chip = chipGroup.findViewById(checkedIds.get(0));
            selectedCategory = (String) chip.getTag();
            prefs.edit().putString(KEY_CATEGORY, selectedCategory).apply();
            loadItems();
            // 칩은 목록 밖에 있어 스크롤한 채로 누를 수 있다. 다른 분류는 처음부터 보여 준다.
            recycler.scrollToPosition(0);
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

    private void loadItems() {
        // 화면을 열어 둔 채 날짜가 바뀌어도 돌아왔을 때 맞도록 매번 쓴다.
        tvDate.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(new Date()));

        List<TodayItem> items = database.getItems(CATEGORY_ALL.equals(selectedCategory) ? null : selectedCategory);
        adapter.setItems(items);

        long lastReceived = database.getLastReceivedAt();
        tvLastReceived.setText(lastReceived > 0
                ? "마지막 수신 " + new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN).format(new Date(lastReceived))
                : "");

        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) {
            tvEmpty.setText(lastReceived > 0
                    ? "이 분류에 받은 소식이 없습니다."
                    : "아직 받은 소식이 없습니다.\n서버에서 보내면 여기에 표시됩니다.");
        }
    }

    private void openItem(TodayItem item, int position) {
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
