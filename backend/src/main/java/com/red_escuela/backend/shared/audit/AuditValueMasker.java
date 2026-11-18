package com.red_escuela.backend.shared.audit;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuditValueMasker {

    public static final String MASK = "***";

    private static final List<String> SENSITIVE_SUFFIXES = List.of("password", "passwordhash", "token", "refreshtoken",
            "code", "secret", "apikey", "cbu");

    private static final Pattern KEY_VALUE = Pattern.compile(
            "([\"']?)([A-Za-z][A-Za-z0-9_\\-]*)\\1(\\s*[:=]\\s*)(\"(?:[^\"\\\\]|\\\\.)*(?:\"|$)|'[^']*(?:'|$)|[^\\s,}\\]\"']+)");

    private AuditValueMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = KEY_VALUE.matcher(value);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group(4);
            boolean quoted = raw.startsWith("\"") || raw.startsWith("'");
            String replacement = matcher.group();
            if (isSensitive(matcher.group(2)) && !"null".equals(raw)) {
                String quote = matcher.group(1).isEmpty() && quoted ? raw.substring(0, 1) : "\"";
                String maskedValue = quoted ? quote + MASK + quote : MASK;
                replacement = matcher.group(1) + matcher.group(2) + matcher.group(1) + matcher.group(3) + maskedValue;
            }
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private static boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }
}
