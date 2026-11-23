package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DebtExceptionDocumentIssuers {

    private final Map<CertificateDocumentType, DebtExceptionDocumentIssuer> byType = new EnumMap<>(CertificateDocumentType.class);

    public DebtExceptionDocumentIssuers(List<DebtExceptionDocumentIssuer> issuers) {
        for (CertificateDocumentType type : CertificateDocumentType.values()) {
            List<DebtExceptionDocumentIssuer> supporting = issuers.stream().filter(issuer -> issuer.supports(type)).toList();
            if (supporting.size() > 1) {
                throw new IllegalStateException("Hay más de un emisor de excepción por deuda para " + type);
            }
            if (supporting.size() == 1) {
                byType.put(type, supporting.get(0));
            }
        }
    }

    public Optional<DebtExceptionDocumentIssuer> forType(CertificateDocumentType type) {
        return Optional.ofNullable(byType.get(type));
    }
}
