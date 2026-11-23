package com.red_escuela.backend.identity.repository;

import java.time.Instant;

public interface PermissionDiscrepancyDirectionTotalRow {

    String getModule();

    String getDirection();

    long getOccurrences();

    Instant getLastSeenAt();
}
