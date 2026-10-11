package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface PreProcessingResult {

    static PreProcessingResult proceed() {
        return Proceed.INSTANCE;
    }

    static PreProcessingResult requireAuthorization(AuthorizationRole requiredRole, String reason) {
        return new RequiresAuthorization(requiredRole, reason, null);
    }

    static PreProcessingResult requireAuthorizationUntil(AuthorizationRole requiredRole, String reason, LocalDate expiresOn) {
        Objects.requireNonNull(expiresOn, "expiresOn");
        return new RequiresAuthorization(requiredRole, reason, expiresOn);
    }

    static PreProcessingResult replayStoredOutcome(BillingOperationOutcome<?> storedOutcome) {
        return new ReplayStoredOutcome(storedOutcome);
    }

    enum Proceed implements PreProcessingResult {
        INSTANCE
    }

    record RequiresAuthorization(AuthorizationRole requiredRole, String reason, LocalDate expiresOn) implements PreProcessingResult {
        public RequiresAuthorization {
            Objects.requireNonNull(requiredRole, "requiredRole");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record ReplayStoredOutcome(BillingOperationOutcome<?> storedOutcome) implements PreProcessingResult {
        public ReplayStoredOutcome {
            Objects.requireNonNull(storedOutcome, "storedOutcome");
        }
    }
}
