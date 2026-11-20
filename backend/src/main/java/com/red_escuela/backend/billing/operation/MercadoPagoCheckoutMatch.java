package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

public record MercadoPagoCheckoutMatch(Long invoiceId, Money expectedAmount, boolean expired) {
}
