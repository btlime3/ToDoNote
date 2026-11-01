package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import kr.akotis.todonote.model.DraftItem;
import kr.akotis.todonote.model.MemoItem;
import kr.akotis.todonote.model.NoteItem;

@RunWith(AndroidJUnit4.class)
public class DraftStorageTest {
    private Context context;
    private String databaseName;
    private DatabaseHelper helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "draft_test_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, databaseName);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void noteDraftsAreKeptPerNoteAndReplaced() {
        helper.saveNoteDraft(DatabaseHelper.DRAFT_NEW_ITEM, "새 제목", "새 내용");
        helper.saveNoteDraft(7, "제목", "첫 내용");
        helper.saveNoteDraft(7, "제목", "둘째 내용");

        DraftItem newNote = helper.getNoteDraft(DatabaseHelper.DRAFT_NEW_ITEM);
        assertEquals("새 내용", newNote.getContent());

        DraftItem existing = helper.getNoteDraft(7);
        assertEquals(7, existing.getTargetId());
        assertEquals("둘째 내용", existing.getContent());
        assertTrue(existing.getSavedAt() > 0);

        helper.deleteNoteDraft(7);
        assertNull(helper.getNoteDraft(7));
        assertNotNull(helper.getNoteDraft(DatabaseHelper.DRAFT_NEW_ITEM));
    }

    @Test
    public void memoDraftKeepsOnlyTheLatestAndIsSeparateFromNotes() {
        helper.saveNoteDraft(3, "노트", "노트 내용");
        helper.saveMemoDraft(DatabaseHelper.DRAFT_NEW_ITEM, "새 메모");
        helper.saveMemoDraft(3, "수정 중인 메모");

        DraftItem draft = helper.getMemoDraft();
        assertEquals(3, draft.getTargetId());
        assertEquals("수정 중인 메모", draft.getContent());

        helper.deleteMemoDraft();
        assertNull(helper.getMemoDraft());
        assertEquals("노트 내용", helper.getNoteDraft(3).getContent());
    }

    @Test
    public void deletingNoteRemovesOnlyThatNotesDraft() {
        long keep = helper.insertNote(new NoteItem("유지", "내용"));
        long remove = helper.insertNote(new NoteItem("삭제", "내용"));
        helper.saveNoteDraft(keep, "유지", "수정");
        helper.saveNoteDraft(remove, "삭제", "수정");

        assertEquals(1, helper.deleteNote(remove));

        assertNull(helper.getNoteDraft(remove));
        assertNotNull(helper.getNoteDraft(keep));
        assertEquals(1, helper.getDeletedNotes().size());
    }

    @Test
    public void memoLookupReportsDeletedState() {
        long id = helper.insertMemo(new MemoItem("메모"));
        assertEquals(0, helper.getMemoById(id).getDeletedAt());

        helper.deleteMemo(id);

        assertTrue(helper.getMemoById(id).getDeletedAt() > 0);
        assertNull(helper.getMemoById(id + 100));
    }
}
