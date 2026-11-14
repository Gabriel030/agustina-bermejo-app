package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.LegalGuardianConfirmation;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.GuardianConfirmationRequestedEvent;
import com.red_escuela.backend.identity.repository.LegalGuardianConfirmationRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class GuardianConfirmationIssuer {

    private final LegalGuardianConfirmationRepository confirmationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String frontendBaseUrl;
    private final Duration timeToLive;
    private final SecureRandom secureRandom = new SecureRandom();

    public GuardianConfirmationIssuer(LegalGuardianConfirmationRepository confirmationRepository,
                                      ApplicationEventPublisher eventPublisher, Clock clock,
                                      @Value("${app.frontend.base-url}") String frontendBaseUrl,
                                      @Value("${app.legal.guardian-confirmation-ttl-hours}") long timeToLiveHours) {
        this.confirmationRepository = confirmationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        this.timeToLive = Duration.ofHours(timeToLiveHours);
    }

    @Transactional
    public void issue(Person minor, String guardianName, String guardianEmail, String guardianRelationship) {
        LocalDateTime now = LocalDateTime.now(clock);
        confirmationRepository.expireOpenOnes(minor.getId(), now);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        confirmationRepository.save(new LegalGuardianConfirmation(minor.getId(), guardianName.trim(), guardianEmail.trim(),
                guardianRelationship.trim(), TokenHashUtil.sha256Hex(clearToken), now, now.plus(timeToLive)));
        eventPublisher.publishEvent(GuardianConfirmationRequestedEvent.requestedFor(guardianName.trim(), guardianEmail.trim(),
                minor.getName(), frontendBaseUrl + "/confirmar-responsable?token=" + clearToken));
    }
}
