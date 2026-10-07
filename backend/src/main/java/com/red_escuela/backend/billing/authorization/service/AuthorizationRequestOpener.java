package com.red_escuela.backend.billing.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.events.AuthorizationRequestedEvent;
import com.red_escuela.backend.billing.operation.AuthorizationRequestPort;
import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class AuthorizationRequestOpener implements AuthorizationRequestPort {
    private final AuthorizationRequestRepository repository;
    private final CurrentPersonService currentPersonService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AuthorizationRequestOpener(AuthorizationRequestRepository repository, CurrentPersonService currentPersonService,
                                      ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.currentPersonService = currentPersonService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Long openAuthorizationRequest(BillingOperationType type, Object context, AuthorizationRole requiredRole, String reason, LocalDate expiresOn) {
        AuthorizationRequest request = repository.save(AuthorizationRequest.open(type, serialize(context), requiredRole, reason,
                currentPersonService.resolveCurrentPerson(), expiresOn, Instant.now(clock)));
        eventPublisher.publishEvent(AuthorizationRequestedEvent.requestedFor(request.getId(), type.name(), requiredRole.name(), reason));
        return request.getId();
    }

    private String serialize(Object context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Operation context cannot be serialized for an authorization request", e);
        }
    }
}
