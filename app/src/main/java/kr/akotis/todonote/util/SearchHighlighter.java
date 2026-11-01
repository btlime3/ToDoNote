package kr.akotis.todonote.util;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;

import androidx.annotation.ColorInt;

/** 검색어와 일치하는 부분에 배경색을 입힌다. */
public final class SearchHighlighter {
    private SearchHighlighter() {
    }

    public static CharSequence highlight(String text, SearchQuery query, @ColorInt int color) {
        if (text == null) {
            return "";
        }
        if (query == null || query.isEmpty()) {
            return text;
        }
        SpannableString spannable = new SpannableString(text);
        for (String term : query.getTerms()) {
            int index = SearchQuery.indexOf(text, term, 0);
            while (index >= 0) {
                spannable.setSpan(new BackgroundColorSpan(color), index, index + term.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = SearchQuery.indexOf(text, term, index + term.length());
            }
        }
        return spannable;
    }
}
