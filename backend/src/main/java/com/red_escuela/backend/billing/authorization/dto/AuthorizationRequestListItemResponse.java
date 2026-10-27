package com.red_escuela.backend.billing.authorization.dto;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationOperationCatalog;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationOperationFamily;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxEntry;
import com.red_escuela.backend.shared.domain.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record AuthorizationRequestListItemResponse(Long id, String operationType, String operationLabel, String family, String familyLabel, String status,
                                                   String resumeOutcome, String summary, String studentName, Money amount, AuthorizationRequestPersonResponse requestedBy,
                                                   Instant requestedAt, LocalDate expiresOn, String requiredRole, Instant decidedAt) {

    public static AuthorizationRequestListItemResponse from(InboxEntry entry, Map<Long, String> personNames) {
        AuthorizationRequest request = entry.request();
        AuthorizationOperationFamily family = AuthorizationOperationCatalog.familyOf(request.getOperationType());
        return new AuthorizationRequestListItemResponse(request.getId(), request.getOperationType().name(), AuthorizationOperationCatalog.labelOf(request.getOperationType()),
                family.name(), family.label(), request.getStatus().name(), request.getResumeOutcome() == null ? null : request.getResumeOutcome().name(),
                entry.description().summary(), entry.description().studentName(), entry.description().amount(),
                AuthorizationRequestPersonResponse.of(request.getRequestedBy(), personNames, entry.requesterRoles()), request.getRequestedAt(), request.getExpiresOn(),
                request.getRequiredRole().name(), request.getDecidedAt());
    }
}
