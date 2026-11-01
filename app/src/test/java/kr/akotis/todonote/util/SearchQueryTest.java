package kr.akotis.todonote.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchQueryTest {

    @Test
    public void blankInputIsEmptyAndMatchesEverything() {
        assertTrue(SearchQuery.parse(null).isEmpty());
        assertTrue(SearchQuery.parse("   ").isEmpty());
        assertTrue(SearchQuery.parse("").matches("아무 내용"));
    }

    @Test
    public void matchingIgnoresCaseAndFindsKorean() {
        assertTrue(SearchQuery.parse("HELLO").matches("say hello there"));
        assertTrue(SearchQuery.parse("회의").matches("오늘 회의록"));
        assertFalse(SearchQuery.parse("회식").matches("오늘 회의록"));
    }

    @Test
    public void everyTermMustAppearButMayBeInDifferentFields() {
        SearchQuery query = SearchQuery.parse("  회의   메모 ");
        assertEquals(2, query.getTerms().size());
        assertTrue(query.matches("회의 제목", "메모 내용"));
        assertFalse(query.matches("회의 제목", "다른 내용"));
    }

    @Test
    public void nullFieldsAreIgnored() {
        assertTrue(SearchQuery.parse("내용").matches(null, "내용"));
        assertFalse(SearchQuery.parse("내용").matches((String) null));
    }

    @Test
    public void indexOfKeepsOriginalPositions() {
        assertEquals(4, SearchQuery.indexOf("abc ABC", "abc", 1));
        assertEquals(-1, SearchQuery.indexOf("abc", "abcd", 0));
        assertEquals(-1, SearchQuery.indexOf("abc", "abc", 1));
    }

    @Test
    public void shortTextIsFlattenedWithoutEllipsis() {
        assertEquals("첫 줄 둘째 줄", SearchQuery.parse("둘째").excerpt("첫 줄\n\n둘째   줄", 100));
    }

    @Test
    public void longTextShowsTheFirstMatch() {
        String text = "가".repeat(100) + "찾는말" + "나".repeat(100);
        String excerpt = SearchQuery.parse("찾는말").excerpt(text, 40);
        assertTrue(excerpt.contains("찾는말"));
        assertTrue(excerpt.startsWith("…"));
        assertTrue(excerpt.endsWith("…"));
        assertEquals(42, excerpt.length());
    }

    @Test
    public void matchAtTheEndDoesNotOverrun() {
        String text = "가".repeat(100) + "끝말";
        String excerpt = SearchQuery.parse("끝말").excerpt(text, 40);
        assertTrue(excerpt.endsWith("끝말"));
        assertEquals(41, excerpt.length());
    }

    @Test
    public void noMatchStartsFromTheBeginning() {
        String excerpt = SearchQuery.parse("없는말").excerpt("가".repeat(100), 40);
        assertTrue(excerpt.startsWith("가"));
        assertTrue(excerpt.endsWith("…"));
    }
}
