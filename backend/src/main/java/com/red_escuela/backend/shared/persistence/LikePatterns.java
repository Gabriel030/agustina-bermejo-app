package com.red_escuela.backend.shared.persistence;

public final class LikePatterns {

    public static final char ESCAPE_CHARACTER = '\\';

    private LikePatterns() {
    }

    public static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static String contains(String raw) {
        return "%" + escape(raw) + "%";
    }
}
