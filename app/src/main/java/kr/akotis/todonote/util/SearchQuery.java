package kr.akotis.todonote.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 공백으로 나눈 검색어를 모두 포함하는지 대소문자 구분 없이 판단한다. */
public final class SearchQuery {
    private static final SearchQuery EMPTY = new SearchQuery(Collections.emptyList());

    private final List<String> terms;

    private SearchQuery(List<String> terms) {
        this.terms = terms;
    }

    public static SearchQuery parse(String raw) {
        String trimmed = raw != null ? raw.trim() : "";
        return trimmed.isEmpty() ? EMPTY : new SearchQuery(Arrays.asList(trimmed.split("\\s+")));
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public List<String> getTerms() {
        return terms;
    }

    /** 각 검색어가 주어진 항목 중 하나에 들어 있으면 일치한다. 검색어가 없으면 항상 일치한다. */
    public boolean matches(String... fields) {
        for (String term : terms) {
            if (!containsInAny(fields, term)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsInAny(String[] fields, String term) {
        for (String field : fields) {
            if (field != null && indexOf(field, term, 0) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** 대소문자를 무시한 위치를 찾는다. 소문자 변환과 달리 원문의 위치가 어긋나지 않는다. */
    public static int indexOf(String text, String term, int fromIndex) {
        int last = text.length() - term.length();
        for (int i = Math.max(fromIndex, 0); i <= last; i++) {
            if (text.regionMatches(true, i, term, 0, term.length())) {
                return i;
            }
        }
        return -1;
    }

    /** 검색어가 처음 나오는 부분을 중심으로 한 줄 요약을 만든다. */
    public String excerpt(String text, int maxLength) {
        String flat = text != null ? text.replaceAll("\\s+", " ").trim() : "";
        if (flat.length() <= maxLength) {
            return flat;
        }
        int match = -1;
        for (String term : terms) {
            int index = indexOf(flat, term, 0);
            if (index >= 0 && (match < 0 || index < match)) {
                match = index;
            }
        }
        int start = Math.min(Math.max(match - EXCERPT_LEAD, 0), flat.length() - maxLength);
        int end = start + maxLength;
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }

    private static final int EXCERPT_LEAD = 20;
}
