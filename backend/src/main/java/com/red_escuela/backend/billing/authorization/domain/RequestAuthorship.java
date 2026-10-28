package com.red_escuela.backend.billing.authorization.domain;

import java.time.Instant;

public record RequestAuthorship(Long requestedByPersonId, Long decidedByPersonId, Instant decidedAt) {
}
