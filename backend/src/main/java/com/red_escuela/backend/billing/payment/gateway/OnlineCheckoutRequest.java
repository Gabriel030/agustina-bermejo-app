package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;

public record OnlineCheckoutRequest(Long studentCurrentAccountId, NonNegativeMoney amount, List<Long> invoiceIds, String idempotencyKey) {

    public OnlineCheckoutRequest {
        invoiceIds = List.copyOf(invoiceIds);
    }
}
