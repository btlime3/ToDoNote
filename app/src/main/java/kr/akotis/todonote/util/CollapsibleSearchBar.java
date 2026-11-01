package kr.akotis.todonote.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import kr.akotis.todonote.R;

/** 화살표를 눌러 여닫는 작은 검색란. 화면별로 마지막 상태를 기억한다. */
public final class CollapsibleSearchBar {
    private static final String PREFS = "search_bar";

    private final View field;
    private final View arrow;
    private final View toggle;
    private final EditText input;

    private CollapsibleSearchBar(View root) {
        field = root.findViewById(R.id.search_field);
        arrow = root.findViewById(R.id.search_arrow);
        toggle = root.findViewById(R.id.search_toggle);
        input = root.findViewById(R.id.et_search);

        View clear = root.findViewById(R.id.btn_search_clear);
        clear.setOnClickListener(v -> input.setText(""));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    /** 접을 수 있는 검색란. 처음에는 접혀 있고, 이후에는 이 화면의 마지막 상태로 열린다. */
    public static void attach(View root, String screenKey) {
        CollapsibleSearchBar bar = new CollapsibleSearchBar(root);
        SharedPreferences prefs = root.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        bar.show(prefs.getBoolean(screenKey, false));
        bar.toggle.setOnClickListener(v -> {
            boolean expanded = bar.field.getVisibility() != View.VISIBLE;
            prefs.edit().putBoolean(screenKey, expanded).apply();
            bar.show(expanded);
            if (expanded) {
                bar.focusInput();
            } else {
                // 접힌 채로 검색 결과만 남아 있으면 메모가 사라진 것처럼 보인다.
                bar.input.setText("");
                bar.hideKeyboard();
            }
        });
    }

    /** 검색 전용 화면에서는 접지 않는다. */
    public static void attachAlwaysOpen(View root) {
        CollapsibleSearchBar bar = new CollapsibleSearchBar(root);
        bar.toggle.setVisibility(View.GONE);
        bar.field.setVisibility(View.VISIBLE);
    }

    private void show(boolean expanded) {
        field.setVisibility(expanded ? View.VISIBLE : View.GONE);
        arrow.setRotation(expanded ? 180 : 0);
        toggle.setContentDescription(expanded ? "검색 닫기" : "검색 열기");
    }

    private void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        input.clearFocus();
        InputMethodManager imm = (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }
}
