package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionNotFoundException;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentPlanOptionFinder {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;

    public PaymentPlanOptionFinder(PaymentPlanOptionRepository paymentPlanOptionRepository) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
    }

    public PaymentPlanOption findOrThrow(Long id) {
        return paymentPlanOptionRepository.findById(id)
                .orElseThrow(() -> new PaymentPlanOptionNotFoundException(id));
    }
}
