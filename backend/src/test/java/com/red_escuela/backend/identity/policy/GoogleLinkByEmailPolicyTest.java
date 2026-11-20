package com.red_escuela.backend.identity.policy;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.PublicPasswordResetBlockPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private static final Long OWNER_ID = 7L;

    private final EmailVerificationTokenRepository tokenRepository = mock(EmailVerificationTokenRepository.class);
    private final ContactChangeRecorder contactChangeRecorder = mock(ContactChangeRecorder.class);
    private final PublicPasswordResetBlockPolicy blockPolicy = new PublicPasswordResetBlockPolicy(tokenRepository, contactChangeRecorder);

    private Person verifiedOwnerWithPendingChangeRequestedBy(Long requesterId) {
        Person owner = personWithEmailVerified(true);
        owner.setId(OWNER_ID);
        when(tokenRepository.existsByPersonAndNewEmailIsNotNull(owner)).thenReturn(true);
        when(contactChangeRecorder.lastEmailChangeRequesterId(OWNER_ID)).thenReturn(Optional.of(requesterId));
        return owner;
    }

    @Test
    void changeStartedByTheOwnerDoesNotBlockTheLink() {
        Person owner = verifiedOwnerWithPendingChangeRequestedBy(OWNER_ID);

        assertThatCode(() -> GoogleLinkByEmailPolicy.requireAllowed(owner, blockPolicy.blocks(owner))).doesNotThrowAnyException();
    }

    @Test
    void pendingChangeStartedByStaffBlocksTheLink() {
        Person owner = verifiedOwnerWithPendingChangeRequestedBy(99L);

        assertThatThrownBy(() -> GoogleLinkByEmailPolicy.requireAllowed(owner, blockPolicy.blocks(owner)))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
