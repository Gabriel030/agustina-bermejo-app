package com.red_escuela.backend.billing.exception;

public class ScholarshipExceedsCohortSpanException extends RuntimeException {
    public ScholarshipExceedsCohortSpanException(int installments, int maxInstallments) {
        super("installments (" + installments + ") no puede superar la cantidad de cuotas del cohorte ("
                + maxInstallments + ") — una beca no puede cubrir más de un cohorte");
    }
}
