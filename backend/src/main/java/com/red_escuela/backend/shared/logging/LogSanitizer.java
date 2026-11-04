package com.red_escuela.backend.shared.logging;

public final class LogSanitizer {
    public static final int MAX_LENGTH = 200;
    private static final String TRUNCATION_MARK = "...";

    private LogSanitizer() {
    }

    public static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        String text = value.toString();
        boolean truncated = text.length() > MAX_LENGTH;
        String bounded = truncated ? text.substring(0, MAX_LENGTH) : text;
        StringBuilder clean = new StringBuilder(bounded.length() + TRUNCATION_MARK.length());
        bounded.codePoints().forEach(codePoint -> clean.appendCodePoint(Character.isISOControl(codePoint) || isLineSeparator(codePoint) ? '_' : codePoint));
        return truncated ? clean.append(TRUNCATION_MARK).toString() : clean.toString();
    }

    private static boolean isLineSeparator(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}
