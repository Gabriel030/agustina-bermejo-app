package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.LegalGuardianConfirmation;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.GuardianConfirmationNotApplicableException;
import com.red_escuela.backend.identity.exception.GuardianConfirmationResendLimitException;
import com.red_escuela.backend.identity.repository.LegalGuardianConfirmationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class GuardianConfirmationResender {

    private final CurrentPersonService currentPersonService;
    private final LegalMinorChecker legalMinorChecker;
    private final LegalAcceptanceRecorder legalAcceptanceRecorder;
    private final LegalGuardianConfirmationRepository confirmationRepository;
    private final GuardianConfirmationIssuer issuer;
    private final Clock clock;
    private final Duration cooldown;
    private final int maxRequestsPerDay;

    public GuardianConfirmationResender(CurrentPersonService currentPersonService, LegalMinorChecker legalMinorChecker,
                                        LegalAcceptanceRecorder legalAcceptanceRecorder,
                                        LegalGuardianConfirmationRepository confirmationRepository,
                                        GuardianConfirmationIssuer issuer, Clock clock,
                                        @Value("${app.legal.guardian-resend-cooldown-minutes}") long cooldownMinutes,
                                        @Value("${app.legal.guardian-max-requests-per-day}") int maxRequestsPerDay) {
        this.currentPersonService = currentPersonService;
        this.legalMinorChecker = legalMinorChecker;
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
        this.confirmationRepository = confirmationRepository;
        this.issuer = issuer;
        this.clock = clock;
        this.cooldown = Duration.ofMinutes(cooldownMinutes);
        this.maxRequestsPerDay = maxRequestsPerDay;
    }

    @Transactional
    public void resendForCurrentPerson() {
        Person person = currentPersonService.resolveCurrentPerson();
        if (!legalMinorChecker.isMinor(person) || legalAcceptanceRecorder.pendingDocuments(person.getId()).isEmpty()) {
            throw new GuardianConfirmationNotApplicableException();
        }
        LegalGuardianConfirmation last = confirmationRepository.findFirstByPersonIdOrderByCreatedAtDesc(person.getId())
                .orElseThrow(GuardianConfirmationNotApplicableException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime nextAllowed = last.getCreatedAt().plus(cooldown);
        if (nextAllowed.isAfter(now)) {
            throw new GuardianConfirmationResendLimitException(Duration.between(now, nextAllowed));
        }
        if (confirmationRepository.countByPersonIdAndCreatedAtAfter(person.getId(), now.minusDays(1)) >= maxRequestsPerDay) {
            throw new GuardianConfirmationResendLimitException(Duration.ofHours(1));
        }
        issuer.issue(person, last.getGuardianName(), last.getGuardianEmail(), last.getGuardianRelationship());
    }
}
