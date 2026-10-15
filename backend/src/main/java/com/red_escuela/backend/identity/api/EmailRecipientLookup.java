package com.red_escuela.backend.identity.api;

import java.util.Optional;

public interface EmailRecipientLookup {

    Optional<EmailRecipient> findEmailRecipient(Long personId);
}
