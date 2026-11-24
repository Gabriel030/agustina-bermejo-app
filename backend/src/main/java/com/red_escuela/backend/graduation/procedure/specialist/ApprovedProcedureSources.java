package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ApprovedProcedureSources {

    private final Map<CertificateDocumentType, ApprovedProcedureSource> byType = new EnumMap<>(CertificateDocumentType.class);

    public ApprovedProcedureSources(List<ApprovedProcedureSource> sources) {
        for (ApprovedProcedureSource source : sources) {
            if (byType.putIfAbsent(source.documentType(), source) != null) {
                throw new IllegalStateException("Hay dos fuentes de trámites aprobados para " + source.documentType());
            }
        }
    }

    public List<ApprovedProcedureSource> all() {
        return List.copyOf(byType.values());
    }

    public Optional<ApprovedProcedureSource> forType(CertificateDocumentType documentType) {
        return Optional.ofNullable(byType.get(documentType));
    }
}
