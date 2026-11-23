package com.red_escuela.backend.graduation.api;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DebtExceptionGate {

    private final DebtExceptionConsumption consumer;

    public DebtExceptionGate(DebtExceptionConsumption consumer) {
        this.consumer = consumer;
    }

    public Optional<Long> consumeActiveException(Long studentId, CertificateDocumentType documentType, Long registrationId) {
        return consumer.consumeActive(studentId, documentType, registrationId);
    }
}
