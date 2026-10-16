package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.LateFeeSettlementSource;

import java.util.UUID;

public final class LateFeeOverchargeMarker {
    public static final String HANDLER_NAME = "LateFeeOverchargeRecorder";
    public static final String ALLOCATION_MARKER_SQL = "lpad(to_hex(a.id), 32, '0')::uuid";
    public static final String CREDIT_NOTE_MARKER_SQL = "(lpad('1', 16, '0') || lpad(to_hex(n.id), 16, '0'))::uuid";
    private static final long CREDIT_NOTE_NAMESPACE = 1L;

    private LateFeeOverchargeMarker() {
    }

    public static UUID markerOf(Long allocationId) {
        return new UUID(0L, allocationId);
    }

    public static UUID markerOfCreditNote(Long creditNoteId) {
        return new UUID(CREDIT_NOTE_NAMESPACE, creditNoteId);
    }

    public static UUID markerOf(LateFeeSettlementSource source) {
        return source.allocationId().map(LateFeeOverchargeMarker::markerOf).orElseGet(() -> markerOfCreditNote(source.creditNoteId().orElseThrow()));
    }
}
