package com.red_escuela.backend.billing.operation;

import java.math.BigDecimal;
import java.util.UUID;

public record NewMercadoPagoPreference(
        String title,
        BigDecimal amount,
        UUID publicReference,
        String successUrl,
        String failureUrl,
        String pendingUrl,
        String notificationUrl) {
}
