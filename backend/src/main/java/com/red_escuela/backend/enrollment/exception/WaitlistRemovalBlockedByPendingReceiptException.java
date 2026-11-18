package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class WaitlistRemovalBlockedByPendingReceiptException extends ConflictException {
    public WaitlistRemovalBlockedByPendingReceiptException() {
        super("No se puede quitar: tiene un comprobante enviado sin resolver");
    }
}
