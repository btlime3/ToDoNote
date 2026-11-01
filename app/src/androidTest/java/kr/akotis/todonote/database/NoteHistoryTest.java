package kr.akotis.todonote.database;

import static org.junit.Assert.assertEquals;
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

import kr.akotis.todonote.model.NoteItem;
import kr.akotis.todonote.model.NoteVersion;

@RunWith(AndroidJUnit4.class)
public class NoteHistoryTest {
    private Context context;
    private String databaseName;
    private DatabaseHelper helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "note_history_test_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, databaseName);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(databaseName);
    }

    private long insert(String title, String content) {
        return helper.insertNote(new NoteItem(title, content));
    }

    private void update(long id, String title, String content) {
        NoteItem note = helper.getNoteById(id);
        note.setTitle(title);
        note.setContent(content);
        assertEquals(1, helper.updateNote(note));
    }

    @Test
    public void savingChangedTextKeepsThePreviousVersionWithItsOriginalTime() {
        long id = insert("제목", "첫 내용");
        long originalTime = helper.getNoteById(id).getUpdatedAt();

        update(id, "제목", "둘째 내용");

        List<NoteVersion> versions = helper.getNoteHistory(id);
        assertEquals(1, versions.size());
        assertEquals("제목", versions.get(0).getTitle());
        assertEquals("첫 내용", versions.get(0).getContent());
        assertEquals(originalTime, versions.get(0).getSavedAt());
        assertEquals("둘째 내용", helper.getNoteById(id).getContent());
    }

    @Test
    public void titleChangeIsAlsoKept() {
        long id = insert("옛 제목", "내용");

        update(id, "새 제목", "내용");

        assertEquals("옛 제목", helper.getNoteHistory(id).get(0).getTitle());
    }

    @Test
    public void savingOrLockingWithoutTextChangeAddsNoVersion() {
        long id = insert("제목", "내용");

        update(id, "제목", "내용");
        helper.toggleNoteLock(id, true);
        update(id, "제목", "내용");

        assertTrue(helper.getNoteHistory(id).isEmpty());
        assertTrue(helper.getNoteById(id).isLocked());
    }

    @Test
    public void historyIsNewestFirstAndLimitedPerNote() {
        long id = insert("제목", "내용 0");
        long other = insert("다른 노트", "다른 내용");
        update(other, "다른 노트", "다른 내용 수정");

        int updates = DatabaseHelper.MAX_NOTE_VERSIONS + 5;
        for (int i = 1; i <= updates; i++) {
            update(id, "제목", "내용 " + i);
        }

        List<NoteVersion> versions = helper.getNoteHistory(id);
        assertEquals(DatabaseHelper.MAX_NOTE_VERSIONS, versions.size());
        assertEquals("내용 " + (updates - 1), versions.get(0).getContent());
        assertEquals("내용 " + (updates - DatabaseHelper.MAX_NOTE_VERSIONS),
                versions.get(DatabaseHelper.MAX_NOTE_VERSIONS - 1).getContent());
        assertEquals(1, helper.getNoteHistory(other).size());
    }

    @Test
    public void historySurvivesTrashAndRestore() {
        long id = insert("제목", "첫 내용");
        update(id, "제목", "둘째 내용");

        helper.deleteNote(id);
        assertEquals(1, helper.getNoteHistory(id).size());

        helper.restoreNote(id);
        assertEquals(1, helper.getNoteHistory(id).size());
        assertEquals("둘째 내용", helper.getNoteById(id).getContent());
    }

    @Test
    public void permanentDeleteRemovesOnlyThatNotesHistory() {
        long removed = insert("삭제", "첫 내용");
        long kept = insert("유지", "첫 내용");
        update(removed, "삭제", "수정");
        update(kept, "유지", "수정");

        helper.deleteNote(removed);
        helper.permanentlyDeleteNote(removed);

        assertTrue(helper.getNoteHistory(removed).isEmpty());
        assertEquals(1, helper.getNoteHistory(kept).size());
    }

    @Test
    public void emptyingTrashRemovesHistoryAndDraftsOfTrashedNotesOnly() {
        long trashed = insert("삭제", "첫 내용");
        long kept = insert("유지", "첫 내용");
        update(trashed, "삭제", "수정");
        update(kept, "유지", "수정");
        helper.deleteNote(trashed);
        // 휴지통에 있는 동안 남은 임시 저장본도 비울 때 함께 지워져야 한다.
        helper.saveNoteDraft(trashed, "삭제", "임시");
        helper.saveNoteDraft(kept, "유지", "임시");

        assertEquals(1, helper.emptyTrash());

        assertTrue(helper.getNoteHistory(trashed).isEmpty());
        assertEquals(null, helper.getNoteDraft(trashed));
        assertEquals(1, helper.getNoteHistory(kept).size());
        assertEquals("임시", helper.getNoteDraft(kept).getContent());
        assertEquals("수정", helper.getNoteById(kept).getContent());
    }

    @Test
    public void deletingHistoryKeepsTheCurrentNote() {
        long id = insert("제목", "첫 내용");
        update(id, "제목", "둘째 내용");

        assertEquals(1, helper.deleteNoteHistory(id));

        assertTrue(helper.getNoteHistory(id).isEmpty());
        assertEquals("둘째 내용", helper.getNoteById(id).getContent());
    }
}
