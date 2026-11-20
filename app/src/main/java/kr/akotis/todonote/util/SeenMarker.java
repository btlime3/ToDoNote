package kr.akotis.todonote.util;

import java.util.List;

/**
 * "여기부터 이미 본 소식" 구분선의 위치를 정한다.
 * 목록은 발행 시각 순서이므로 새로 받은 글과 이미 본 글이 섞일 수 있다. 새 글을 모두 선 위에 두려고
 * 마지막 새 글 바로 아래에 선을 긋는다.
 */
public final class SeenMarker {
    private SeenMarker() {
    }

    /**
     * @param receivedTimes 화면 순서대로 각 글을 받은 시각
     * @param seenUntil     지난번에 이 탭을 볼 때까지 받은 마지막 시각. 0이면 처음 보는 것이다
     * @return 이 순번의 글 앞에 선을 긋는다. 새 글이나 이미 본 글이 없으면 -1
     */
    public static int position(List<Long> receivedTimes, long seenUntil) {
        if (seenUntil <= 0) {
            return -1;
        }
        int lastNew = -1;
        for (int i = 0; i < receivedTimes.size(); i++) {
            if (receivedTimes.get(i) > seenUntil) {
                lastNew = i;
            }
        }
        return lastNew >= 0 && lastNew < receivedTimes.size() - 1 ? lastNew + 1 : -1;
    }
}
