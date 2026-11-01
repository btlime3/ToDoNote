package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;

import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.NoteItem;

@RunWith(AndroidJUnit4.class)
public class PinningTest {
    private Context context;
    private String databaseName;
    private DatabaseHelper helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "pinning_test_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, databaseName);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void pinnedMemosComeFirstAndKeepTheirOrder() {
        long first = helper.insertMemo(new MemoItem("첫째"));
        long second = helper.insertMemo(new MemoItem("둘째"));
        long third = helper.insertMemo(new MemoItem("셋째"));

        // 새 메모가 위로 오므로 처음 순서는 셋째, 둘째, 첫째
        assertEquals(1, helper.setMemoPinned(first, true));

        List<MemoItem> memos = helper.getAllMemos();
        assertEquals(first, memos.get(0).getId());
        assertTrue(memos.get(0).isPinned());
        assertEquals(third, memos.get(1).getId());
        assertEquals(second, memos.get(2).getId());
        assertFalse(memos.get(1).isPinned());

        helper.setMemoPinned(first, false);
        assertEquals(third, helper.getAllMemos().get(0).getId());
    }

    @Test
    public void newMemoStaysBelowPinnedMemos() {
        long pinned = helper.insertMemo(new MemoItem("고정"));
        helper.setMemoPinned(pinned, true);

        long newest = helper.insertMemo(new MemoItem("새 메모"));

        List<MemoItem> memos = helper.getAllMemos();
        assertEquals(pinned, memos.get(0).getId());
        assertEquals(newest, memos.get(1).getId());
    }

    @Test
    public void reorderingKeepsPinnedGroupOnTop() {
        long a = helper.insertMemo(new MemoItem("a"));
        long b = helper.insertMemo(new MemoItem("b"));
        long c = helper.insertMemo(new MemoItem("c"));
        helper.setMemoPinned(a, true);
        helper.setMemoPinned(b, true);

        List<MemoItem> memos = helper.getAllMemos();
        // 화면에서 고정 그룹 안의 두 항목을 맞바꾼 결과를 저장
        MemoItem top = memos.remove(0);
        memos.add(1, top);
        helper.updateMemoOrder(memos);

        List<MemoItem> reloaded = helper.getAllMemos();
        assertEquals(memos.get(0).getId(), reloaded.get(0).getId());
        assertEquals(memos.get(1).getId(), reloaded.get(1).getId());
        assertEquals(c, reloaded.get(2).getId());
    }

    @Test
    public void pinningKeepsContentAndUpdateTime() {
        long memoId = helper.insertMemo(new MemoItem("메모"));
        long noteId = helper.insertNote(new NoteItem("제목", "내용"));
        long memoTime = helper.getMemoById(memoId).getUpdatedAt();
        long noteTime = helper.getNoteById(noteId).getUpdatedAt();

        helper.setMemoPinned(memoId, true);
        helper.setNotePinned(noteId, true);

        assertEquals(memoTime, helper.getMemoById(memoId).getUpdatedAt());
        assertEquals(noteTime, helper.getNoteById(noteId).getUpdatedAt());
        assertEquals("메모", helper.getMemoById(memoId).getContent());
        assertEquals("내용", helper.getNoteById(noteId).getContent());
        assertTrue(helper.getNoteHistory(noteId).isEmpty());
    }

    @Test
    public void pinnedNotesComeFirstAndSavingKeepsThePin() {
        long first = helper.insertNote(new NoteItem("첫째", "내용"));
        long second = helper.insertNote(new NoteItem("둘째", "내용"));

        helper.setNotePinned(first, true);
        assertEquals(first, helper.getAllNotes().get(0).getId());
        assertEquals(second, helper.getAllNotes().get(1).getId());

        NoteItem note = helper.getNoteById(first);
        note.setContent("수정");
        helper.updateNote(note);

        assertTrue(helper.getNoteById(first).isPinned());
        assertEquals(first, helper.getAllNotes().get(0).getId());
    }

    @Test
    public void pinSurvivesTrashAndRestore() {
        long id = helper.insertNote(new NoteItem("제목", "내용"));
        helper.setNotePinned(id, true);

        helper.deleteNote(id);
        assertTrue(helper.getAllNotes().isEmpty());

        helper.restoreNote(id);
        assertTrue(helper.getAllNotes().get(0).isPinned());
    }
}
