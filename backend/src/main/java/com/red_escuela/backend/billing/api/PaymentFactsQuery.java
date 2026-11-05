package com.red_escuela.backend.billing.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PaymentFactsQuery {

    List<PaymentArrival> findPaymentArrivalsOf(Collection<Long> personIds, Instant from, Instant until);
}
