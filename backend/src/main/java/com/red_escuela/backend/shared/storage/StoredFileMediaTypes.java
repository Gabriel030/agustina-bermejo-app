package com.red_escuela.backend.shared.storage;

import java.util.Locale;
import java.util.Map;

public final class StoredFileMediaTypes {

    private static final Map<String, String> BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");
    private static final String DEFAULT = "application/octet-stream";

    private StoredFileMediaTypes() {
    }

    public static String of(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT;
        }
        return BY_EXTENSION.getOrDefault(fileName.substring(dot + 1).toLowerCase(Locale.ROOT), DEFAULT);
    }
}
