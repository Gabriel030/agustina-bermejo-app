package com.red_escuela.backend.identity.policy;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleLinkByEmailPolicyTest {

    private Person personWithEmailVerified(Boolean verified) {
        Person person = new Person();
        person.setEmailVerified(verified);
        return person;
    }

    @Test
    void verifiedEmailWithoutStaffChangeIsAllowed() {
        assertThatCode(() -> GoogleLinkByEmailPolicy.requireAllowed(personWithEmailVerified(true), false)).doesNotThrowAnyException();
    }

    @Test
    void unverifiedEmailIsRejected() {
        assertThatThrownBy(() -> GoogleLinkByEmailPolicy.requireAllowed(personWithEmailVerified(false), false))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownVerificationIsRejected() {
        assertThatThrownBy(() -> GoogleLinkByEmailPolicy.requireAllowed(personWithEmailVerified(null), false))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void blockingStaffChangeIsRejectedEvenWithVerifiedEmail() {
        assertThatThrownBy(() -> GoogleLinkByEmailPolicy.requireAllowed(personWithEmailVerified(true), true))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
