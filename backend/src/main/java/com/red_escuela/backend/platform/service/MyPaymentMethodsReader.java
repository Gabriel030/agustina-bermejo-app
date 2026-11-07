package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.api.PaymentMethodAvailability;
import com.red_escuela.backend.platform.domain.MyPaymentMethod;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyPaymentMethodsReader {

    private final PaymentMethodAvailability paymentMethodAvailability;

    public MyPaymentMethodsReader(PaymentMethodAvailability paymentMethodAvailability) {
        this.paymentMethodAvailability = paymentMethodAvailability;
    }

    public List<MyPaymentMethod> readAll() {
        return paymentMethodAvailability.statusOfAll().stream().map(status -> new MyPaymentMethod(status.code(), status.effective())).toList();
    }
}
