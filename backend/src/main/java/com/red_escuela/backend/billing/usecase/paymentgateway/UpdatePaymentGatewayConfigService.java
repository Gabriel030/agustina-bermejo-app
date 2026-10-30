package com.red_escuela.backend.billing.usecase.paymentgateway;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.PaymentGatewayConfigResponse;
import com.red_escuela.backend.billing.dto.UpdatePaymentGatewayConfigRequest;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfigChange;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigChangeRepository;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@UseCase
public class UpdatePaymentGatewayConfigService implements UpdatePaymentGatewayConfigUseCase {

    private final PaymentGatewayConfigRepository configRepository;
    private final PaymentGatewayConfigChangeRepository changeRepository;
    private final Clock clock;

    public UpdatePaymentGatewayConfigService(
            PaymentGatewayConfigRepository configRepository,
            PaymentGatewayConfigChangeRepository changeRepository,
            Clock clock) {
        this.configRepository = configRepository;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentGatewayConfigResponse update(UpdatePaymentGatewayConfigRequest request, Person updatedBy) {
        PaymentGatewayConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseGet(PaymentGatewayConfig::new);
        PaymentGatewayConfigChange change = new PaymentGatewayConfigChange();
        change.setMpEnabledBefore(config.isMpEnabled());
        change.setBankAliasBefore(config.getBankAlias());
        change.setBankCbuBefore(config.getBankCbu());
        change.setAccountHolderBefore(config.getAccountHolder());
        change.setQrImagePathBefore(config.getQrImagePath());
        change.setQrImagePathAfter(config.getQrImagePath());

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
        LocalDateTime now = LocalDateTime.now(clock);
        config.setUpdatedAt(now);
        config.setUpdatedBy(updatedBy);

        PaymentGatewayConfig saved = configRepository.save(config);
        change.setMpEnabledAfter(saved.isMpEnabled());
        change.setBankAliasAfter(saved.getBankAlias());
        change.setBankCbuAfter(saved.getBankCbu());
        change.setAccountHolderAfter(saved.getAccountHolder());
        if (hasDifferences(change)) {
            change.setChangedAt(now);
            change.setChangedBy(updatedBy);
            changeRepository.save(change);
        }
        String qrImageUrl = saved.getQrImagePath() != null ? "/payment-config/qr-image" : null;
        return PaymentGatewayConfigResponse.from(saved, qrImageUrl);
    }

    private boolean hasDifferences(PaymentGatewayConfigChange change) {
        return change.isMpEnabledBefore() != change.isMpEnabledAfter()
                || !Objects.equals(change.getBankAliasBefore(), change.getBankAliasAfter())
                || !Objects.equals(change.getBankCbuBefore(), change.getBankCbuAfter())
                || !Objects.equals(change.getAccountHolderBefore(), change.getAccountHolderAfter());
    }
}
