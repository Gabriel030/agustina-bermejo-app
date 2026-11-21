package com.red_escuela.backend.identity.repository;

import java.time.Instant;

public interface PermissionDiscrepancyRow {

    String getRole();

    String getModule();

    String getPermissionCode();

    String getDirection();

    long getOccurrences();

    Instant getLastSeenAt();
}
