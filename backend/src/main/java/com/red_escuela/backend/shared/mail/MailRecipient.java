package com.red_escuela.backend.shared.mail;

import java.util.Objects;

public record MailRecipient(Long personId, String name, String email) {

    public MailRecipient {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
    }
}
