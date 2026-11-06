package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.Map;

public class LastDigitalPaymentMethodRequiredException extends ConflictException implements ReportsErrorDetails {
    public static final String OPEN_ONLINE_INVOICES_KEY = "openOnlineInvoices";

    private final long openOnlineInvoices;

    public LastDigitalPaymentMethodRequiredException(PaymentMethodCode code, long openOnlineInvoices) {
        super("No se puede apagar " + code + ": es el último medio de pago digital y hay " + openOnlineInvoices + " facturas online abiertas");
        this.openOnlineInvoices = openOnlineInvoices;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(OPEN_ONLINE_INVOICES_KEY, openOnlineInvoices);
    }
}
