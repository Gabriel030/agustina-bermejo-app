package com.red_escuela.backend.notification.invitation.domain;

import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;

public record InvitationWaveParameters(String name, int batchSize, int intervalMinutes, int dailyCap) {

    public static final int MAX_NAME_LENGTH = 160;
    public static final int MAX_INTERVAL_MINUTES = 1440;

    public InvitationWaveParameters {
        if (name == null || name.isBlank()) {
            throw new RequestFieldRequiredException("name is required");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new RequestFieldOutOfRangeException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (batchSize < 1) {
            throw new RequestFieldOutOfRangeException("batchSize must be positive");
        }
        if (intervalMinutes < 1 || intervalMinutes > MAX_INTERVAL_MINUTES) {
            throw new RequestFieldOutOfRangeException("intervalMinutes must be between 1 and " + MAX_INTERVAL_MINUTES);
        }
        if (dailyCap < 1) {
            throw new RequestFieldOutOfRangeException("dailyCap must be positive");
        }
        if (batchSize > dailyCap) {
            throw new RequestFieldOutOfRangeException("batchSize must not exceed dailyCap");
        }
    }

    public InvitationWaveParameters requireWithinGlobalDailyLimit(int globalDailyLimit) {
        if (dailyCap > globalDailyLimit) {
            throw new RequestFieldOutOfRangeException("dailyCap must not exceed the global daily limit of " + globalDailyLimit);
        }
        return this;
    }
}
