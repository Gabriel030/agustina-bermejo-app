package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetPaymentGatewayConfigService implements GetPaymentGatewayConfigUseCase {

    private final PaymentGatewayConfigRepository configRepository;

    public GetPaymentGatewayConfigService(PaymentGatewayConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public PaymentGatewayConfigResponse get() {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::emptyConfig);
        String qrImageUrl = config.getQrImagePath() != null ? "/payment-config/qr-image" : null;
        return PaymentGatewayConfigResponse.from(config, qrImageUrl);
    }

    private PaymentGatewayConfig emptyConfig() {
        return new PaymentGatewayConfig();
    }
}
