package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDateTime;

public interface PaymentGatewayConfigChangeRecordingPort {

    PaymentGatewayConfigSnapshot captureSnapshot(PaymentGatewayConfig config);

    void recordChangeIfDifferent(PaymentGatewayConfigSnapshot before, PaymentGatewayConfigSnapshot after,
                                 Person changedBy, LocalDateTime changedAt);
}
