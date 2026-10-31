package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnnualUpfrontDeadlineExpirer {
    private static final Logger LOG = LoggerFactory.getLogger(AnnualUpfrontDeadlineExpirer.class);

    private final AnnualUpfrontRequestRepository requestRepository;
    private final AnnualUpfrontRequestDeadlineDiscarder discarder;
    private final Clock clock;

    public AnnualUpfrontDeadlineExpirer(AnnualUpfrontRequestRepository requestRepository, AnnualUpfrontRequestDeadlineDiscarder discarder, Clock clock) {
        this.requestRepository = requestRepository;
        this.discarder = discarder;
        this.clock = clock;
    }

    public AnnualUpfrontExpiryResult expireRequestsPastDeadline() {
        List<Long> candidates = requestRepository.findIdsAwaitingPaymentWithDeadlineBefore(LocalDateTime.now(clock));
        int expired = 0;
        int skipped = 0;
        List<Long> failed = new ArrayList<>();
        for (Long requestId : candidates) {
            try {
                if (discarder.discardRequestIfDeadlineExpiredWithoutPayment(requestId)) {
                    expired++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException failure) {
                LOG.error("No se pudo descartar por vencimiento el pedido de pago anual {}", requestId, failure);
                failed.add(requestId);
            }
        }
        return new AnnualUpfrontExpiryResult(expired, skipped, failed);
    }
}
