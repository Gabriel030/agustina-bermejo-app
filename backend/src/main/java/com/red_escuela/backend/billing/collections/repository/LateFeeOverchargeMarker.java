package com.red_escuela.backend.billing.collections.repository;

import java.util.UUID;

public final class LateFeeOverchargeMarker {
    public static final String HANDLER_NAME = "LateFeeOverchargeRecorder";
    public static final String ALLOCATION_MARKER_SQL = "lpad(to_hex(a.id), 32, '0')::uuid";

    private LateFeeOverchargeMarker() {
    }

    public static UUID markerOf(Long allocationId) {
        return new UUID(0L, allocationId);
    }
}
