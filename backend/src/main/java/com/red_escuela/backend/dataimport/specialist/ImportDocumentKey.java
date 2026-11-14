package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.shared.domain.DocumentType;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ImportDocumentKey {
    private ImportDocumentKey() {
    }

    public static Optional<DocumentType> typeOf(Map<String, String> payload) {
        String raw = ImportValueParser.cleanText(payload.get("documentType"));
        if (raw.isEmpty()) {
            return Optional.of(DocumentType.DNI);
        }
        try {
            return Optional.of(DocumentType.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static String numberOf(Map<String, String> payload, DocumentType type) {
        String cleaned = ImportValueParser.cleanDocumentNumber(payload.get("dni"));
        return type == DocumentType.DNI ? cleaned : cleaned.toUpperCase(Locale.ROOT);
    }

    public static String keyOf(Map<String, String> payload) {
        DocumentType type = typeOf(payload).orElse(DocumentType.DNI);
        return type + ":" + numberOf(payload, type);
    }

    public static DocumentType typeOfKey(String key) {
        return DocumentType.valueOf(key.substring(0, key.indexOf(':')));
    }

    public static String numberOfKey(String key) {
        return key.substring(key.indexOf(':') + 1);
    }
}
