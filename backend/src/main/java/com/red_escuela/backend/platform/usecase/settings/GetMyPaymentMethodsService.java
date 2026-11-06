package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.api.PaymentMethodAvailability;
import com.red_escuela.backend.platform.api.PaymentMethodAvailability.PaymentMethodStatus;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetMyPaymentMethodsService implements GetMyPaymentMethodsUseCase {

    private final PaymentMethodAvailability paymentMethodAvailability;

    public GetMyPaymentMethodsService(PaymentMethodAvailability paymentMethodAvailability) {
        this.paymentMethodAvailability = paymentMethodAvailability;
    }

    @Override
    public List<PaymentMethodStatus> read() {
        return paymentMethodAvailability.statusOfAll();
    }
}
