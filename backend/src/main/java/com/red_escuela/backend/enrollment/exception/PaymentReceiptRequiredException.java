package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class PaymentReceiptRequiredException extends BadRequestException {
    public PaymentReceiptRequiredException(Long invoiceId) {
        super("La factura " + invoiceId + " sigue impaga y requiere un comprobante");
    }
}
