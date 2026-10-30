package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.operation.PaymentGatewayConfigChangeRecordingPort;
import com.red_escuela.backend.billing.operation.PaymentGatewayConfigSnapshot;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayConfigChange;
import com.red_escuela.backend.billing.payment.repository.PaymentGatewayConfigChangeRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentGatewayConfigChangeRecorder implements PaymentGatewayConfigChangeRecordingPort {

    private final PaymentGatewayConfigChangeRepository changeRepository;

    public PaymentGatewayConfigChangeRecorder(PaymentGatewayConfigChangeRepository changeRepository) {
        this.changeRepository = changeRepository;
    }

    @Override
    public PaymentGatewayConfigSnapshot captureSnapshot(PaymentGatewayConfig config) {
        return new PaymentGatewayConfigSnapshot(config.isMpEnabled(), config.getBankAlias(), config.getBankCbu(),
                config.getAccountHolder(), config.getQrImagePath());
    }

    @Override
    public void recordChangeIfDifferent(PaymentGatewayConfigSnapshot before, PaymentGatewayConfigSnapshot after,
                                        Person changedBy, LocalDateTime changedAt) {
        if (before.equals(after)) {
            return;
        }
        changeRepository.save(PaymentGatewayConfigChange.builder()
                .changedAt(changedAt)
                .changedBy(changedBy)
                .mpEnabledBefore(before.mpEnabled())
                .mpEnabledAfter(after.mpEnabled())
                .bankAliasBefore(before.bankAlias())
                .bankAliasAfter(after.bankAlias())
                .bankCbuBefore(before.bankCbu())
                .bankCbuAfter(after.bankCbu())
                .accountHolderBefore(before.accountHolder())
                .accountHolderAfter(after.accountHolder())
                .qrImagePathBefore(before.qrImagePath())
                .qrImagePathAfter(after.qrImagePath())
                .build());
    }
}
