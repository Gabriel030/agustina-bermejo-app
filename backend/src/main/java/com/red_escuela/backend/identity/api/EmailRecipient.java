package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.domain.EmailAddress;

import java.util.Objects;

public record EmailRecipient(Long personId, EmailAddress address, boolean verified) {

    public EmailRecipient {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(address, "address");
    }
}
