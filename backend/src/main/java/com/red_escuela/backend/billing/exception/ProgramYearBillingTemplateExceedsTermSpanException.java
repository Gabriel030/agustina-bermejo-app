package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ProgramYearBillingTemplateExceedsTermSpanException extends BadRequestException {
    public ProgramYearBillingTemplateExceedsTermSpanException(Integer recurringFeeInvoicesCount, long termSpanMonths) {
        super("recurringFeeInvoicesCount (" + recurringFeeInvoicesCount + ") no puede superar la duración del Term ("
                + termSpanMonths + " meses) — un pago no puede cubrir más de un cohorte");
    }
}
