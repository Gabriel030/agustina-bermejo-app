package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.exception.BadRequestException;

public class GatewayCurrencyNotSupportedException extends BadRequestException {
    public GatewayCurrencyNotSupportedException(Long invoiceId, Currency currency) {
        super("Invoice " + invoiceId + " is in " + currency + " and the payment gateway only supports ARS");
    }
}
