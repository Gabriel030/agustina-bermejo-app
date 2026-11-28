package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class BillingActivationBlockedByCohortsWithoutTemplateException extends ConflictException {
    private final long cohortCount;

    public BillingActivationBlockedByCohortsWithoutTemplateException(long cohortCount) {
        super("No se puede activar la facturación: hay " + cohortCount + " cohorte(s) sin plantilla de facturación. Asignales una plantilla antes de activarla");
        this.cohortCount = cohortCount;
    }

    public long cohortCount() {
        return cohortCount;
    }
}
