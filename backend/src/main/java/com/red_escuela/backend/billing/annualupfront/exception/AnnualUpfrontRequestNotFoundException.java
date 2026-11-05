package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AnnualUpfrontRequestNotFoundException extends NotFoundException {
    public AnnualUpfrontRequestNotFoundException(Long requestId) {
        super("Pedido de pago anual no encontrado: " + requestId);
    }
}
