package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ScholarshipExceedsCohortSpanException extends BadRequestException {
    public ScholarshipExceedsCohortSpanException(int installments, int maxInstallments) {
        super("installments (" + installments + ") no puede superar la cantidad de cuotas del cohorte ("
                + maxInstallments + ") — una beca no puede cubrir más de un cohorte");
    }
}
