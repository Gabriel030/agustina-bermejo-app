package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProposalNoLongerOpenException extends ConflictException {
    public ProposalNoLongerOpenException(Long authorizationRequestId) {
        super("La propuesta de precio del pedido " + authorizationRequestId + " ya no está abierta.");
    }
}
