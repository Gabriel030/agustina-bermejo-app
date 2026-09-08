package com.red_escuela.backend.billing.exception;

public class ProgramYearBillingTemplateExceedsTermSpanException extends RuntimeException {
    public ProgramYearBillingTemplateExceedsTermSpanException(Integer recurringFeeInvoicesCount, long termSpanMonths) {
        super("recurringFeeInvoicesCount (" + recurringFeeInvoicesCount + ") no puede superar la duración del Term ("
                + termSpanMonths + " meses) — un pago no puede cubrir más de un cohorte");
    }
}
