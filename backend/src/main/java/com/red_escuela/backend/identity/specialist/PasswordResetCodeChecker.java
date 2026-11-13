package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.PasswordResetLockoutNoticeEvent;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PasswordResetCodeChecker {
    private static final String ACCOUNT_WIDE_SOURCE = "*";
    private static final String UNKNOWN_SOURCE = "unknown";

    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final int sourceMaxFailures;
    private final Duration sourceWindow;
    private final int accountMaxFailures;
    private final Duration accountWindow;
    private final Duration noticeInterval;

    public PasswordResetCodeChecker(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                    ApplicationEventPublisher eventPublisher, Clock clock,
                                    @Value("${auth.password-reset-lockout.max-attempts}") int sourceMaxFailures,
                                    @Value("${auth.password-reset-lockout.duration-minutes}") long sourceWindowMinutes,
                                    @Value("${auth.password-reset-lockout.account-max-failures}") int accountMaxFailures,
                                    @Value("${auth.password-reset-lockout.account-window-hours}") long accountWindowHours,
                                    @Value("${auth.password-reset-lockout.notice-interval-minutes}") long noticeIntervalMinutes) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.sourceMaxFailures = sourceMaxFailures;
        this.sourceWindow = Duration.ofMinutes(sourceWindowMinutes);
        this.accountMaxFailures = accountMaxFailures;
        this.accountWindow = Duration.ofHours(accountWindowHours);
        this.noticeInterval = Duration.ofMinutes(noticeIntervalMinutes);
    }

    public void requireNotLocked(Person person, String ipAddress) {
        long blocking = passwordResetTokenRepository.countBlockingFailures(
                person.getId(), sourceOf(ipAddress), sourceMaxFailures, now().minus(sourceWindow));
        if (blocking > 0) {
            throw new InvalidPasswordResetTokenException();
        }
    }

    public void clearFailedAttempts(Person person, String ipAddress) {
        passwordResetTokenRepository.clearFailures(person.getId(), sourceOf(ipAddress));
    }

    public void registerFailedAttempt(Person person, PasswordResetToken resetToken, String ipAddress) {
        LocalDateTime now = now();
        int sourceFailures = passwordResetTokenRepository.registerFailure(
                person.getId(), sourceOf(ipAddress), now, now.minus(sourceWindow));
        if (sourceFailures == sourceMaxFailures) {
            noticeOwner(person, now);
        }
        int accountFailures = passwordResetTokenRepository.registerFailure(
                person.getId(), ACCOUNT_WIDE_SOURCE, now, now.minus(accountWindow));
        if (accountFailures >= accountMaxFailures) {
            passwordResetTokenRepository.invalidateActiveCodes(person.getId());
            passwordResetTokenRepository.clearFailures(person.getId(), ACCOUNT_WIDE_SOURCE);
            resetToken.setUsed(true);
        }
    }

    private void noticeOwner(Person person, LocalDateTime now) {
        int claimed = personRepository.claimResetLockoutNotice(person.getId(), now, now.minus(noticeInterval));
        if (claimed > 0) {
            eventPublisher.publishEvent(PasswordResetLockoutNoticeEvent.forPerson(person.getId()));
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String sourceOf(String ipAddress) {
        return TokenHashUtil.sha256Hex(ipAddress == null || ipAddress.isBlank() ? UNKNOWN_SOURCE : ipAddress);
    }
}
