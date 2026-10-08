package com.red_escuela.backend.billing.usecase.payment;

import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.identity.entity.Person;

public interface RegisterPaymentUseCase {
    Payment register(RegisterPaymentRequest request, Person registeredBy);

    Payment registerPaymentShortOfInvoiceBalance(RegisterPaymentRequest request, Person registeredBy);
}
