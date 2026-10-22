package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.ReceiptInvoiceIdsLookup;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.billing.payment.repository.PaymentCheckoutItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CheckoutItemReceiptInvoiceIdsLookup implements ReceiptInvoiceIdsLookup {
    private final PaymentCheckoutItemRepository checkoutItems;

    public CheckoutItemReceiptInvoiceIdsLookup(PaymentCheckoutItemRepository checkoutItems) {
        this.checkoutItems = checkoutItems;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findInvoiceIdsByPaymentId(Collection<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return checkoutItems.findByPaymentIdInOrderByPaymentIdAscPositionAsc(paymentIds).stream()
                .collect(Collectors.groupingBy(PaymentCheckoutItem::getPaymentId, LinkedHashMap::new,
                        Collectors.mapping(PaymentCheckoutItem::getInvoiceId, Collectors.toList())));
    }
}
