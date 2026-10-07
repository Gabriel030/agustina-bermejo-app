package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;

public interface AuthorizationRequestPort {
    Long openAuthorizationRequest(BillingOperationType type, Object context, AuthorizationRole requiredRole, String reason, LocalDate expiresOn);
}
