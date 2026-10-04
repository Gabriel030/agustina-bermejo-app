package com.red_escuela.backend.billing.usecase.paymentgateway;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.dto.UpdatePaymentGatewayConfigRequest;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class UpdatePaymentGatewayConfigService implements UpdatePaymentGatewayConfigUseCase {

    private final PaymentGatewayConfigRepository configRepository;
    private final Clock clock;

    public UpdatePaymentGatewayConfigService(PaymentGatewayConfigRepository configRepository, Clock clock) {
        this.configRepository = configRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentGatewayConfigResponse update(UpdatePaymentGatewayConfigRequest request, Person updatedBy) {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(PaymentGatewayConfig::new);

        if (request.bankAlias() != null) {
            config.setBankAlias(request.bankAlias());
        }
        if (request.bankCbu() != null) {
            config.setBankCbu(request.bankCbu());
        }
        if (request.accountHolder() != null) {
            config.setAccountHolder(request.accountHolder());
        }
        if (request.mpEnabled() != null) {
            config.setMpEnabled(request.mpEnabled());
        }
        config.setUpdatedAt(LocalDateTime.now(clock));
        config.setUpdatedBy(updatedBy);

        PaymentGatewayConfig saved = configRepository.save(config);
        String qrImageUrl = saved.getQrImagePath() != null ? "/payment-config/qr-image" : null;
        return PaymentGatewayConfigResponse.from(saved, qrImageUrl);
    }
}
