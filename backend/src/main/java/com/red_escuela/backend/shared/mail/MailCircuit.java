package com.red_escuela.backend.shared.mail;

import jakarta.mail.SendFailedException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;

public final class MailCircuit {
    public static final String NAME = "mail";

    private MailCircuit() {
    }

    public static boolean countsAsProviderFailure(Throwable failure) {
        if (failure instanceof MailParseException || failure instanceof MailPreparationException) {
            return false;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException rejected && rejected.getInvalidAddresses() != null
                    && rejected.getInvalidAddresses().length > 0) {
                return false;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return true;
    }
}
