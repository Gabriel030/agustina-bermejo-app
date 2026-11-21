package com.red_escuela.backend.shared.audit;

import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldTooLongException;

import java.util.Optional;

public final class ActorNameTerm {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    public static final int MAX_MATCHES = 200;

    private ActorNameTerm() {
    }

    public static Optional<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String term = raw.trim();
        if (term.length() < MIN_LENGTH) {
            throw new RequestFieldOutOfRangeException("El campo actorName debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (term.length() > MAX_LENGTH) {
            throw new RequestFieldTooLongException("El campo actorName no puede superar los " + MAX_LENGTH + " caracteres");
        }
        return Optional.of(term);
    }
}
