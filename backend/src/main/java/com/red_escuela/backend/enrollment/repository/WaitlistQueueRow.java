package com.red_escuela.backend.enrollment.repository;

import java.time.LocalDateTime;

public interface WaitlistQueueRow {
    Long getId();

    String getName();

    String getTermDescription();

    Integer getTermYear();

    Integer getCapacity();

    Long getOccupied();

    Long getWaiting();

    LocalDateTime getNextExpiry();
}
