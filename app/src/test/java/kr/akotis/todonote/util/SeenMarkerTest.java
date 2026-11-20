package kr.akotis.todonote.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class SeenMarkerTest {

    @Test
    public void lineGoesBelowTheLastNewItem() {
        assertEquals(2, SeenMarker.position(Arrays.asList(30L, 25L, 10L, 5L), 20));
        // 발행 시각 때문에 새 글이 아래쪽에 섞여 있으면 그 글까지 선 위에 둔다.
        assertEquals(3, SeenMarker.position(Arrays.asList(30L, 10L, 25L, 5L), 20));
    }

    @Test
    public void noLineWithoutBothNewAndSeenItems() {
        assertEquals(-1, SeenMarker.position(Arrays.asList(30L, 25L), 0));
        assertEquals(-1, SeenMarker.position(Arrays.asList(30L, 25L), 20));
        assertEquals(-1, SeenMarker.position(Arrays.asList(10L, 5L), 20));
        assertEquals(-1, SeenMarker.position(Arrays.asList(5L, 30L), 20));
    }
}
