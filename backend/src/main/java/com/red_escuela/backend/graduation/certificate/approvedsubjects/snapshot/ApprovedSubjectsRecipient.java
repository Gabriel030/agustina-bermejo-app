package com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;

public record ApprovedSubjectsRecipient(RecipientType type, String text) {

    public static final int MAX_TEXT_LENGTH = 80;

    public ApprovedSubjectsRecipient {
        if (type == null || text == null || text.isEmpty() || text.length() > MAX_TEXT_LENGTH
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Destinatario inválido");
        }
    }
}
