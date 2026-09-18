package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.dto.UpdatePaymentGatewayConfigRequest;
import com.red_escuela.backend.identity.entity.Person;

public interface UpdatePaymentGatewayConfigUseCase {

    PaymentGatewayConfigResponse update(UpdatePaymentGatewayConfigRequest request, Person updatedBy);
}
