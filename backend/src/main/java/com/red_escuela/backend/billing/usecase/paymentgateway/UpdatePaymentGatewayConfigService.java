package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.dto.UpdatePaymentGatewayConfigRequest;
import com.red_escuela.backend.billing.payment.service.UpdatePaymentGatewayConfigTransaction;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdatePaymentGatewayConfigService implements UpdatePaymentGatewayConfigUseCase {

    private final UpdatePaymentGatewayConfigTransaction updateTransaction;

    public UpdatePaymentGatewayConfigService(UpdatePaymentGatewayConfigTransaction updateTransaction) {
        this.updateTransaction = updateTransaction;
    }

    @Override
    public PaymentGatewayConfigResponse update(UpdatePaymentGatewayConfigRequest request, Person updatedBy) {
        return updateTransaction.update(request, updatedBy);
    }
}
