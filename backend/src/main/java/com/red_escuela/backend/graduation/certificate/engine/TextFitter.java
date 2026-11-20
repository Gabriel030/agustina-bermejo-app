package com.red_escuela.backend.graduation.certificate.engine;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TextFitter {

    private static final float SHRINK_STEP = 0.5f;
    private static final float LINE_FACTOR = 1.2f;
    private static final String ELLIPSIS = "...";

    private TextFitter() {
    }

    public static FittedText fit(PDFont font, String value, float maxFontSize, float minFontSize, float maxWidth, float maxHeight)
            throws IOException {
        String safe = encodable(font, value == null ? "" : value.trim());
        float size = maxFontSize;
        while (true) {
            List<String> lines = wrap(font, size, maxWidth, safe);
            float lineHeight = size * LINE_FACTOR;
            if (lines.size() * lineHeight <= maxHeight && widest(font, size, lines) <= maxWidth) {
                return new FittedText(lines, size, lineHeight);
            }
            if (size - SHRINK_STEP < minFontSize) {
                return truncated(font, minFontSize, maxWidth, maxHeight, safe);
            }
            size -= SHRINK_STEP;
        }
    }

    public static float width(PDFont font, float fontSize, String value) throws IOException {
        return font.getStringWidth(value) / 1000 * fontSize;
    }

    public static String encodable(PDFont font, String value) {
        StringBuilder safe = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                safe.append(character);
            } catch (IOException | IllegalArgumentException e) {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    private static FittedText truncated(PDFont font, float size, float maxWidth, float maxHeight, String value) throws IOException {
        float lineHeight = size * LINE_FACTOR;
        int maxLines = Math.max(1, (int) Math.floor(maxHeight / lineHeight));
        List<String> lines = wrap(font, size, maxWidth, value);
        if (lines.size() <= maxLines && widest(font, size, lines) <= maxWidth) {
            return new FittedText(lines, size, lineHeight);
        }
        List<String> kept = new ArrayList<>(lines.subList(0, Math.min(lines.size(), maxLines)));
        String last = kept.get(kept.size() - 1);
        if (lines.size() > maxLines) {
            last = last + ELLIPSIS;
        }
        while (last.length() > ELLIPSIS.length() && width(font, size, last) > maxWidth) {
            String body = last.endsWith(ELLIPSIS) ? last.substring(0, last.length() - ELLIPSIS.length()) : last;
            last = body.substring(0, body.length() - 1) + ELLIPSIS;
        }
        kept.set(kept.size() - 1, last);
        return new FittedText(kept, size, lineHeight);
    }

    private static List<String> wrap(PDFont font, float fontSize, float maxWidth, String value) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && width(font, fontSize, candidate) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static float widest(PDFont font, float fontSize, List<String> lines) throws IOException {
        float widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, width(font, fontSize, line));
        }
        return widest;
    }
}
