package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.events.AuthorizationRequestExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class AuthorizationRequestExpirer {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationRequestExpirer.class);

    private final AuthorizationRequestRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AuthorizationRequestExpirer(AuthorizationRequestRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int expireDueRequests() {
        List<AuthorizationRequest> due = repository.findByStatusAndExpiresOnLessThanEqual(AuthorizationRequestStatus.PENDING, LocalDate.now(clock));
        for (AuthorizationRequest request : due) {
            request.expire(Instant.now(clock));
            eventPublisher.publishEvent(AuthorizationRequestExpiredEvent.expiredFor(request.getId(), request.getRequestedBy().getId(), request.getOperationType().name()));
        }
        log.info("Authorization requests expired: count={}", due.size());
        return due.size();
    }
}
