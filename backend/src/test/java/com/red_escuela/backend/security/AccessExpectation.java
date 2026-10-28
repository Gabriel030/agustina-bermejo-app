package com.red_escuela.backend.security;

import java.util.Optional;

enum AccessExpectation {
    ALLOW,
    DENY,
    PUBLIC;

    Optional<String> describeMismatch(int status, boolean anonymousCaller) {
        boolean forbidden = status == 403;
        boolean rejectedAsDenial = forbidden || (anonymousCaller && status == 401);
        return switch (this) {
            case DENY -> {
                if (rejectedAsDenial) {
                    yield Optional.empty();
                }
                if (status == 400) {
                    yield Optional.of("no concluyente: esperado DENY (403) pero la validacion respondio 400 antes de autorizar");
                }
                yield Optional.of("esperado DENY (403), obtenido " + status);
            }
            case ALLOW, PUBLIC -> forbidden
                    ? Optional.of("esperado " + name() + " (distinto de 403), obtenido " + status)
                    : Optional.empty();
        };
    }
}
