package kr.akotis.todonote.util;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.StyleSpan;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 요약을 읽기 쉽게 꾸민다. 서버는 "설명 한 줄 + • 항목" 형식으로 보낸다.
 * 항목이 있으면 첫 줄 설명은 굵게, "• " 줄은 줄바꿈해도 글머리에 맞춰 들여 쓰고, **낱말**은 굵게 보여 준다.
 * 이 형식이 아닌 예전 요약은 그대로 보여 준다.
 */
public final class AiSummaryFormatter {
    private static final String BULLET = "•";
    private static final String BOLD_MARK = "**";

    private AiSummaryFormatter() {
    }

    /** 화면에 보일 글자와 꾸밀 범위. 범위는 [시작, 끝)이다. */
    static final class Parsed {
        final String text;
        final int[] lead;
        final List<int[]> bullets = new ArrayList<>();
        final List<int[]> bold = new ArrayList<>();

        Parsed(String text, int[] lead, List<int[]> bullets, List<int[]> bold) {
            this.text = text;
            this.lead = lead;
            this.bullets.addAll(bullets);
            this.bold.addAll(bold);
        }
    }

    static Parsed parse(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : (raw == null ? "" : raw).split("\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        // 짝이 맞지 않으면 어디까지가 강조인지 알 수 없으므로 표시만 지운다.
        boolean useMarks = count(raw == null ? "" : raw, BOLD_MARK) % 2 == 0;
        StringBuilder text = new StringBuilder();
        List<int[]> bullets = new ArrayList<>();
        List<int[]> bold = new ArrayList<>();
        int[] lead = null;
        boolean hasBullet = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isBullet = line.startsWith(BULLET);
            String content = isBullet ? line.substring(BULLET.length()).trim() : line;
            int start = text.length();
            appendWithMarks(text, content, useMarks, bold);
            int contentEnd = text.length();
            if (i < lines.size() - 1) {
                text.append('\n');
            }
            if (isBullet) {
                hasBullet = true;
                // 단락 꾸밈은 줄바꿈까지 포함해야 한다.
                bullets.add(new int[]{start, text.length()});
            } else if (i == 0) {
                lead = new int[]{start, contentEnd};
            }
        }
        // 항목 없이 문장만 있는 예전 요약을 통째로 굵게 하면 오히려 읽기 어렵다.
        return new Parsed(text.toString(), hasBullet ? lead : null, bullets, bold);
    }

    public static CharSequence format(String raw, @ColorInt int bulletColor, int gapWidth, int bulletRadius) {
        Parsed parsed = parse(raw);
        SpannableString spannable = new SpannableString(parsed.text);
        if (parsed.lead != null) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), parsed.lead[0], parsed.lead[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int[] range : parsed.bullets) {
            spannable.setSpan(new BulletSpan(gapWidth, bulletColor, bulletRadius), range[0], range[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int[] range : parsed.bold) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private static void appendWithMarks(StringBuilder out, String content, boolean useMarks, List<int[]> bold) {
        if (!useMarks) {
            out.append(content.replace(BOLD_MARK, ""));
            return;
        }
        int position = 0;
        int boldStart = -1;
        while (true) {
            int mark = content.indexOf(BOLD_MARK, position);
            if (mark < 0) {
                out.append(content, position, content.length());
                break;
            }
            out.append(content, position, mark);
            if (boldStart < 0) {
                boldStart = out.length();
            } else {
                if (out.length() > boldStart) {
                    bold.add(new int[]{boldStart, out.length()});
                }
                boldStart = -1;
            }
            position = mark + BOLD_MARK.length();
        }
        // 한 줄 안에서 닫히지 않은 강조는 그 줄 끝까지로 본다.
        if (boldStart >= 0 && out.length() > boldStart) {
            bold.add(new int[]{boldStart, out.length()});
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length())) {
            count++;
        }
        return count;
    }
}
