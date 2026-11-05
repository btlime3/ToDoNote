package kr.akotis.todonote.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import kr.akotis.todonote.model.TodayItem;

/**
 * FCM 데이터 메시지의 items 값을 읽는다.
 * 형식: [{"c":"분류","s":"출처","t":"제목","d":"요약","u":"https://...","i":"썸네일 주소(선택)","ai":"AI 요약(선택)","p":발행시각(초)}]
 * 필드 이름을 줄인 것은 FCM 메시지 크기 제한(4KB) 때문이다.
 * "a":1은 1.15까지의 수집기가 보내는 값으로, d가 AI 요약이라는 뜻이다.
 */
public final class TodayFeedParser {
    private static final int MAX_ITEMS_PER_MESSAGE = 100;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_SUMMARY_LENGTH = 600;
    private static final int MAX_LABEL_LENGTH = 40;

    private TodayFeedParser() {
    }

    /** 잘못된 항목은 건너뛰고 나머지를 반환한다. 전체 형식이 잘못되면 빈 목록을 반환한다. */
    public static List<TodayItem> parse(String itemsJson, long now) {
        List<TodayItem> items = new ArrayList<>();
        if (itemsJson == null) {
            return items;
        }
        JSONArray array;
        try {
            array = new JSONArray(itemsJson);
        } catch (JSONException e) {
            return items;
        }
        for (int i = 0; i < array.length() && items.size() < MAX_ITEMS_PER_MESSAGE; i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String url = text(object, "u").trim();
            String title = clip(text(object, "t"), MAX_TITLE_LENGTH);
            if (title.isEmpty() || !isWebUrl(url)) {
                continue;
            }
            TodayItem item = new TodayItem(
                    url,
                    TodayItem.normalizeCategory(text(object, "c")),
                    clip(text(object, "s"), MAX_LABEL_LENGTH),
                    title,
                    clip(text(object, "d"), MAX_SUMMARY_LENGTH),
                    imageUrl(text(object, "i")),
                    publishedAtMillis(object.optLong("p", 0), now));
            item.setAiSummary(object.optInt("a", 0) == 1);
            item.setAiText(clip(text(object, "ai"), MAX_SUMMARY_LENGTH));
            items.add(item);
        }
        return items;
    }

    /** optString은 JSON null을 글자 "null"로 돌려주므로 먼저 확인한다. */
    private static String text(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "");
    }

    /** 카드를 누르면 브라우저로 여는 값이므로 웹 주소만 받는다. */
    private static boolean isWebUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return url.length() <= MAX_URL_LENGTH && (lower.startsWith("https://") || lower.startsWith("http://"));
    }

    /** 앱이 직접 내려받는 주소이므로 https만 받는다. */
    private static String imageUrl(String url) {
        String trimmed = url.trim();
        boolean usable = trimmed.length() <= MAX_URL_LENGTH && trimmed.toLowerCase(Locale.ROOT).startsWith("https://");
        return usable ? trimmed : "";
    }

    /** 날짜가 없거나 미래이면 받은 시각으로 정렬되고 표시되도록 0을 돌려준다. */
    private static long publishedAtMillis(long seconds, long now) {
        long millis = TimeUnit.SECONDS.toMillis(Math.max(seconds, 0));
        return millis > now ? 0 : millis;
    }

    private static String clip(String text, int maxLength) {
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
            end--;
        }
        return trimmed.substring(0, end);
    }
}
