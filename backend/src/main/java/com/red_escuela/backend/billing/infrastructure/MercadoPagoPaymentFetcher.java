package com.red_escuela.backend.billing.infrastructure;

import java.util.Optional;

public interface MercadoPagoPaymentFetcher {
    Optional<MercadoPagoApprovedPayment> fetchApproved(String mpPaymentId);
}
