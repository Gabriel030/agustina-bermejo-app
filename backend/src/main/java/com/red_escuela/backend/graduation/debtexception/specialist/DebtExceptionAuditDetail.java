package com.red_escuela.backend.graduation.debtexception.specialist;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class DebtExceptionAuditDetail {

    private static final Set<String> ALLOWED_KEYS = Set.of("documentId", "code", "engineVersion", "templateVersion");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private DebtExceptionAuditDetail() {
    }

    static String requireAllowed(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        Set<String> seen = new HashSet<>();
        for (String pair : detail.split(";", -1)) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || !ALLOWED_KEYS.contains(pair.substring(0, separator)) || !seen.add(pair.substring(0, separator))
                    || !VALUE.matcher(pair.substring(separator + 1)).matches()) {
                throw new IllegalStateException("El detalle de auditoría del emisor no está permitido");
            }
        }
        return detail;
    }
}
