package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.domain.EmailAddress;

import java.util.Objects;

public record TransactionalEmail(EmailAddress recipient, String subject, String bodyText, String bodyHtml) {

    public TransactionalEmail {
        Objects.requireNonNull(recipient, "recipient");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("A transactional email needs a subject");
        }
        if (bodyText == null || bodyText.isBlank()) {
            throw new IllegalArgumentException("A transactional email needs a plain text body");
        }
    }
}
