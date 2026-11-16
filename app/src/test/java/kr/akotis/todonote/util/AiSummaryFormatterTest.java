package kr.akotis.todonote.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiSummaryFormatterTest {

    @Test
    public void leadIsBoldAndBulletsBecomeIndentedParagraphs() {
        AiSummaryFormatter.Parsed parsed = AiSummaryFormatter.parse("새 모델입니다.\n• 속도 **2배**\n• 오픈소스 공개");
        assertEquals("새 모델입니다.\n속도 2배\n오픈소스 공개", parsed.text);
        assertArrayEquals(new int[]{0, 8}, parsed.lead);
        assertEquals(2, parsed.bullets.size());
        assertArrayEquals(new int[]{9, 15}, parsed.bullets.get(0));
        assertArrayEquals(new int[]{15, 22}, parsed.bullets.get(1));
        assertEquals(1, parsed.bold.size());
        assertEquals("2배", parsed.text.substring(parsed.bold.get(0)[0], parsed.bold.get(0)[1]));
    }

    @Test
    public void oldPlainSummaryStaysAsItIs() {
        AiSummaryFormatter.Parsed parsed = AiSummaryFormatter.parse("문장 하나. 문장 둘.");
        assertEquals("문장 하나. 문장 둘.", parsed.text);
        assertNull(parsed.lead);
        assertTrue(parsed.bullets.isEmpty());
        assertTrue(parsed.bold.isEmpty());
    }

    @Test
    public void unpairedMarksAreRemovedWithoutBold() {
        AiSummaryFormatter.Parsed parsed = AiSummaryFormatter.parse("설명.\n• **강조 하나\n• 끝**이 **둘");
        assertEquals("설명.\n강조 하나\n끝이 둘", parsed.text);
        assertTrue(parsed.bold.isEmpty());
    }

    @Test
    public void emptyLinesAndNullAreHandled() {
        assertEquals("", AiSummaryFormatter.parse(null).text);
        assertEquals("설명.\n항목", AiSummaryFormatter.parse("설명.\n\n•항목\n").text);
    }
}
