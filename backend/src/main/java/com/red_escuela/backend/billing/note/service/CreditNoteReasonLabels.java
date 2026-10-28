package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CreditNoteReason;

final class CreditNoteReasonLabels {

    private CreditNoteReasonLabels() {
    }

    static String of(CreditNoteReason reason) {
        return switch (reason) {
            case BILLING_ERROR -> "Error de facturación";
            case CANCELLATION -> "Cancelación";
            case LATE_FEE_ANNULMENT -> "Anulación de recargo";
            case WITHDRAWAL -> "Baja";
            case LATE_FEE_CORRECTION -> "Corrección de recargo";
        };
    }
}
