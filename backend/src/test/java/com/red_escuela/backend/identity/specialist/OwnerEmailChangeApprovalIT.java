package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.EmailChangeNeedsOwnerEmailException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SharedMockBeans
@RecordApplicationEvents
@Tag("T955")
class OwnerEmailChangeApprovalIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correcta123";

    @Autowired
    private EmailChangeRequester emailChangeRequester;
    @Autowired
    private PendingEmailChangeConfirmer pendingEmailChangeConfirmer;
    @Autowired
    private OwnerEmailChangeApprover ownerEmailChangeApprover;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;
    @Autowired
    private PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ApplicationEvents applicationEvents;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String username, boolean withEmail) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        if (withEmail) {
            person.setEmail(username + "@example.com");
        }
        person.setPassword(passwordEncoder.encode(PASSWORD));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void actAs(Person actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(actor.getId()), null, List.of()));
    }

    private EmailVerificationToken singleTokenOf(Person person) {
        entityManager.flush();
        return tokenRepository.findAll().stream().filter(token -> token.getPerson().getId().equals(person.getId())).findFirst().orElseThrow();
    }

    @Test
    void staffChangeNeedsOwnerApprovalBeforeVerificationMail() {
        Person owner = persistPerson("owner-a", true);
        Person staff = persistPerson("staff-a", true);
        actAs(staff);

        emailChangeRequester.requestStaffChange(owner, "owner-a-new@example.com");

        EmailVerificationToken token = singleTokenOf(owner);
        assertThat(token.isAwaitingOwnerApproval()).isTrue();
        assertThat(token.getOwnerApprovedAt()).isNull();
        assertThat(applicationEvents.stream(EmailChangeRequestedEvent.class)).isEmpty();
        assertThat(applicationEvents.stream(EmailChangeOwnerApprovalRequestedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.personId()).isEqualTo(owner.getId());
            assertThat(event.currentEmail()).isEqualTo("owner-a@example.com");
            assertThat(event.requestedEmail()).isEqualTo("owner-a-new@example.com");
            assertThat(event.reviewLink()).contains("/cancelar-cambio-email?token=");
        });
        assertThat(publicPasswordResetBlockPolicy.blocks(owner)).isTrue();
    }

    @Test
    void newAddressCannotConfirmBeforeOwnerApproves() {
        Person owner = persistPerson("owner-b", true);
        Person staff = persistPerson("staff-b", true);
        actAs(staff);
        emailChangeRequester.requestStaffChange(owner, "owner-b-new@example.com");
        EmailVerificationToken token = singleTokenOf(owner);

        assertThatThrownBy(() -> pendingEmailChangeConfirmer.confirmIfPending(token)).isInstanceOf(InvalidEmailVerificationTokenException.class);
        assertThat(owner.emailValue()).isEqualTo("owner-b@example.com");

        ownerEmailChangeApprover.approve(token);
        pendingEmailChangeConfirmer.confirmIfPending(token);

        assertThat(token.isAwaitingOwnerApproval()).isFalse();
        assertThat(token.getOwnerApprovedAt()).isNotNull();
        assertThat(owner.emailValue()).isEqualTo("owner-b-new@example.com");
    }

    @Test
    void staffChangeRejectedWhenOwnerHasNoCurrentEmail() {
        Person owner = persistPerson("owner-c", false);
        Person staff = persistPerson("staff-c", true);
        actAs(staff);

        assertThatThrownBy(() -> emailChangeRequester.requestStaffChange(owner, "owner-c-new@example.com"))
                .isInstanceOf(EmailChangeNeedsOwnerEmailException.class);

        entityManager.flush();
        assertThat(tokenRepository.findAll().stream().filter(token -> token.getPerson().getId().equals(owner.getId()))).isEmpty();
        assertThat(applicationEvents.stream(EmailChangeOwnerApprovalRequestedEvent.class)).isEmpty();
    }

    @Test
    void ownChangeKeepsCurrentFlow() {
        Person owner = persistPerson("owner-d", true);

        emailChangeRequester.requestOwnChange(owner, "owner-d-new@example.com", PASSWORD);

        EmailVerificationToken token = singleTokenOf(owner);
        assertThat(token.isAwaitingOwnerApproval()).isFalse();
        assertThat(applicationEvents.stream(EmailChangeOwnerApprovalRequestedEvent.class)).isEmpty();
        assertThat(applicationEvents.stream(EmailChangeRequestedEvent.class)).singleElement()
                .satisfies(event -> assertThat(event.verificationLink()).contains("/verificar-email?token="));
        pendingEmailChangeConfirmer.confirmIfPending(token);
        assertThat(owner.emailValue()).isEqualTo("owner-d-new@example.com");
    }

    @Test
    void approvalOfANonAwaitingTokenIsRejected() {
        Person owner = persistPerson("owner-e", true);
        emailChangeRequester.requestOwnChange(owner, "owner-e-new@example.com", PASSWORD);

        assertThatThrownBy(() -> ownerEmailChangeApprover.approve(singleTokenOf(owner))).isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    @Test
    void approvalOfAnExpiredTokenIsRejected() {
        Person owner = persistPerson("owner-f", true);
        Person staff = persistPerson("staff-f", true);
        actAs(staff);
        emailChangeRequester.requestStaffChange(owner, "owner-f-new@example.com");
        EmailVerificationToken token = singleTokenOf(owner);
        token.setExpiresAt(ApplicationTime.now().minus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> ownerEmailChangeApprover.approve(token)).isInstanceOf(InvalidEmailVerificationTokenException.class);
        assertThat(token.isAwaitingOwnerApproval()).isTrue();
    }

    @Test
    void legacyStaffTokenWithoutTheFlagStaysConfirmable() {
        Person owner = persistPerson("owner-g", true);
        EmailVerificationToken legacy = new EmailVerificationToken();
        legacy.setPerson(owner);
        legacy.setTokenHash(TokenHashUtil.sha256Hex("legacy-owner-g"));
        legacy.setNewEmail("owner-g-new@example.com");
        legacy.setExpiresAt(ApplicationTime.now().plusHours(24));
        tokenRepository.saveAndFlush(legacy);

        pendingEmailChangeConfirmer.confirmIfPending(legacy);

        assertThat(owner.emailValue()).isEqualTo("owner-g-new@example.com");
    }

    @Test
    void secondStaffRequestReplacesTheFirstAndStillAwaitsApproval() {
        Person owner = persistPerson("owner-h", true);
        Person staff = persistPerson("staff-h", true);
        actAs(staff);

        emailChangeRequester.requestStaffChange(owner, "owner-h-first@example.com");
        emailChangeRequester.requestStaffChange(owner, "owner-h-second@example.com");

        entityManager.flush();
        List<EmailVerificationToken> tokens = tokenRepository.findAll().stream()
                .filter(token -> token.getPerson().getId().equals(owner.getId())).toList();
        assertThat(tokens).singleElement().satisfies(token -> {
            assertThat(token.getNewEmail()).isEqualTo("owner-h-second@example.com");
            assertThat(token.isAwaitingOwnerApproval()).isTrue();
        });
    }
}
