package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramCohortBillingTemplateRequiredException extends ConflictException {
    public ProgramCohortBillingTemplateRequiredException() {
        super("Con facturación activa la cohorte se crea desde una plantilla de facturación");
    }
}
