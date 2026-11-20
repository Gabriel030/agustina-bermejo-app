package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.PendingEmailChangeConfirmer;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SharedMockBeans
@RecordApplicationEvents
class PublicResetVsStaffEmailChangeTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correcta123";
    private static final String NEW_PASSWORD = "Nueva12345";
    private static final String CODE = "123456";
    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 10, 9, 0);

    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;
    @Autowired
    private VerifyResetCodeUseCase verifyResetCodeUseCase;
    @Autowired
    private ResetPasswordUseCase resetPasswordUseCase;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired
    private ContactChangeRecorder contactChangeRecorder;
    @Autowired
    private PendingEmailChangeConfirmer pendingEmailChangeConfirmer;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ApplicationEvents applicationEvents;

    private Person persistPerson(String username) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode(PASSWORD));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private EmailVerificationToken startEmailChange(Person target, Person requester) {
        contactChangeRecorder.recordChange(target.getId(), requester.getId(), ContactChangeType.EMAIL_CHANGE_REQUESTED,
                target.emailValue(), "new-" + target.emailValue());
        EmailVerificationToken token = new EmailVerificationToken();
        token.setPerson(target);
        token.setTokenHash(TokenHashUtil.sha256Hex("confirm-" + target.getUsername()));
        token.setNewEmail("new-" + target.emailValue());
        token.setExpiresAt(ApplicationTime.now().plusHours(48));
        emailVerificationTokenRepository.saveAndFlush(token);
        return token;
    }

    private void confirmEmailChange(EmailVerificationToken token) {
        pendingEmailChangeConfirmer.confirmIfPending(token);
        emailVerificationTokenRepository.delete(token);
        entityManager.flush();
    }

    private long issuedCodes() {
        return applicationEvents.stream(PasswordResetCodeIssuedEvent.class).count();
    }

    private long resetTokensOf(Person person) {
        return passwordResetTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(person.getId()))
                .count();
    }

    private void requestReset(Person person) {
        requestPasswordResetUseCase.requestReset(person.emailValue());
        entityManager.flush();
    }

    private void issueKnownCode(Person person) {
        requestReset(person);
        PasswordResetToken token = passwordResetTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(person.getId()))
                .reduce((first, second) -> second)
                .orElseThrow();
        token.setTokenHash(TokenHashUtil.sha256Hex(CODE));
        entityManager.flush();
    }

    @Test
    void publicResetIsSilentWhileStaffEmailChangePending() {
        MutableClock.INSTANCE.setDateTime(START);
        Person target = persistPerson("staff-pending-target");
        Person staff = persistPerson("staff-pending-actor");
        startEmailChange(target, staff);

        requestReset(target);

        assertThat(issuedCodes()).isZero();
        assertThat(resetTokensOf(target)).isZero();
    }

    @Test
    void publicResetIsSilentFor72HoursAfterStaffChangeApplied() {
        MutableClock.INSTANCE.setDateTime(START);
        Person target = persistPerson("staff-applied-target");
        Person staff = persistPerson("staff-applied-actor");
        confirmEmailChange(startEmailChange(target, staff));
        MutableClock.INSTANCE.advance(Duration.ofHours(71));

        requestReset(target);

        assertThat(issuedCodes()).isZero();
        assertThat(resetTokensOf(target)).isZero();
    }

    @Test
    void publicResetWorksAfter73Hours() {
        MutableClock.INSTANCE.setDateTime(START);
        Person target = persistPerson("staff-expired-target");
        Person staff = persistPerson("staff-expired-actor");
        confirmEmailChange(startEmailChange(target, staff));
        MutableClock.INSTANCE.advance(Duration.ofHours(73));

        requestReset(target);

        assertThat(issuedCodes()).isEqualTo(1);
        assertThat(resetTokensOf(target)).isEqualTo(1);
    }

    @Test
    void resetTokenIssuedBeforeConfirmationIsRejectedAfter() {
        MutableClock.INSTANCE.setDateTime(START);
        Person target = persistPerson("staff-token-target");
        Person staff = persistPerson("staff-token-actor");
        issueKnownCode(target);
        String exchangeToken = verifyResetCodeUseCase.verify(
                new VerifyResetCodeCommand(target.emailValue(), CODE, "127.0.0.1"));
        confirmEmailChange(startEmailChange(target, staff));

        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, NEW_PASSWORD)))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThat(passwordEncoder.matches(PASSWORD, target.getPassword())).isTrue();
    }

    @Test
    void ownChangeDoesNotBlockPublicReset() {
        MutableClock.INSTANCE.setDateTime(START);
        Person target = persistPerson("own-change-target");
        startEmailChange(target, target);

        requestReset(target);

        assertThat(issuedCodes()).isEqualTo(1);
        assertThat(resetTokensOf(target)).isEqualTo(1);
    }

    @Test
    void codeIssuedBeforeStaffChangeCannotBeVerifiedWhilePending() {
        MutableClock.INSTANCE.setDateTime(START);
        Person target = persistPerson("staff-verify-target");
        Person staff = persistPerson("staff-verify-actor");
        issueKnownCode(target);
        startEmailChange(target, staff);

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(
                new VerifyResetCodeCommand(target.emailValue(), CODE, "127.0.0.1")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }
}
