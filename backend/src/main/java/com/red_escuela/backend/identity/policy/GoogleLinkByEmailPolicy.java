package com.red_escuela.backend.identity.policy;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;

public final class GoogleLinkByEmailPolicy {

    private GoogleLinkByEmailPolicy() {
    }

    public static void requireAllowed(Person person, boolean staffEmailChangeBlocking) {
        if (!Boolean.TRUE.equals(person.getEmailVerified()) || staffEmailChangeBlocking) {
            throw new InvalidCredentialsException();
        }
    }
}
