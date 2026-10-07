package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NoActiveLatePaymentSettingsException extends NotFoundException {
    public NoActiveLatePaymentSettingsException() {
        super("No active LatePaymentSettings — the seed migration should have inserted one");
    }
}
