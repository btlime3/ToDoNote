package kr.akotis.todonote.adapter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import kr.akotis.todonote.adapter.SearchResultAdapter.Item;
import kr.akotis.todonote.adapter.SearchResultAdapter.Type;
import kr.akotis.todonote.util.SearchQuery;

public class SearchResultItemTest {

    private static Item note(boolean locked) {
        return new Item(Type.NOTE, 1, "회의 제목", "비밀 내용", 0, locked, false);
    }

    @Test
    public void lockedNoteIsFoundByTitleButNotByContent() {
        assertTrue(note(true).matches(SearchQuery.parse("회의")));
        assertFalse("잠긴 노트의 내용은 검색으로 짐작할 수 없어야 한다", note(true).matches(SearchQuery.parse("비밀")));
        assertFalse(note(true).matches(SearchQuery.parse("회의 비밀")));
    }

    @Test
    public void unlockedNoteIsFoundByTitleAndContent() {
        assertTrue(note(false).matches(SearchQuery.parse("회의")));
        assertTrue(note(false).matches(SearchQuery.parse("비밀")));
        assertTrue(note(false).matches(SearchQuery.parse("회의 비밀")));
    }

    @Test
    public void memoHasNoTitleAndIsFoundByContent() {
        Item memo = new Item(Type.MEMO, 2, null, "장보기 목록", 0, false, false);
        assertTrue(memo.matches(SearchQuery.parse("장보기")));
        assertFalse(memo.matches(SearchQuery.parse("회의")));
    }
}
