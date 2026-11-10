package com.red_escuela.backend.security;

import java.util.Optional;

enum AccessExpectation {
    ALLOW,
    DENY,
    PUBLIC,
    OWNER;

    record Tolerance(boolean resourceMayBeAbsent, boolean unauthorizedIsAnswer) {

        static final Tolerance NONE = new Tolerance(false, false);
        static final Tolerance ABSENT_RESOURCE = new Tolerance(true, false);
    }

    Optional<String> describeMismatch(int status, boolean anonymousCaller, Tolerance tolerance) {
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
            case ALLOW, PUBLIC, OWNER -> {
                if (forbidden) {
                    yield Optional.of("esperado " + name() + " (distinto de 403), obtenido " + status);
                }
                if (status >= 500) {
                    yield Optional.of("esperado " + name() + " sin error de servidor, obtenido " + status);
                }
                if (status == 401 && this != PUBLIC && !tolerance.unauthorizedIsAnswer()) {
                    yield Optional.of("esperado " + name() + " autenticado, obtenido 401");
                }
                if (status == 404 && !tolerance.resourceMayBeAbsent()) {
                    yield Optional.of("esperado " + name() + " sobre un recurso sin variable de ruta, obtenido 404");
                }
                yield Optional.empty();
            }
        };
    }
}
