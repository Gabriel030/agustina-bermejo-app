package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

public interface PaymentCheckoutItemRepository extends Repository<PaymentCheckoutItem, Long> {

    List<PaymentCheckoutItem> saveAll(Iterable<PaymentCheckoutItem> items);

    List<PaymentCheckoutItem> findByPaymentIdOrderByPositionAsc(Long paymentId);

    List<PaymentCheckoutItem> findByPaymentIdInOrderByPaymentIdAscPositionAsc(Collection<Long> paymentIds);
}
