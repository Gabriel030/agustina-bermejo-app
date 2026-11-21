package com.red_escuela.backend.graduation.certificate.signatory.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatorySummaryResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryTemplateUsageResponse;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatorySignature;

import java.util.Comparator;
import java.util.List;

final class CertificateSignatoryResponses {

    private CertificateSignatoryResponses() {
    }

    static CertificateSignatoryDetailResponse detail(CertificateSignatory signatory, CertificateSignatorySignature signature,
                                                    List<SignatoryTemplateUsageResponse> usages) {
        return new CertificateSignatoryDetailResponse(signatory.getId(), signatory.getName(), signatory.getRole(), signatory.isActive(),
                signature != null, signature == null ? null : signature.getId(), types(signatory), usages, signatory.getCreatedAt(),
                signatory.getUpdatedAt());
    }

    static CertificateSignatorySummaryResponse summary(CertificateSignatory signatory, CertificateSignatorySignature signature,
                                                      List<SignatoryTemplateUsageResponse> usages) {
        return new CertificateSignatorySummaryResponse(signatory.getId(), signatory.getName(), signatory.getRole(), signatory.isActive(),
                signature != null, types(signatory), usages, signatory.getCreatedAt(), signatory.getUpdatedAt());
    }

    static List<SignatoryDocumentType> types(CertificateSignatory signatory) {
        return signatory.getAllowedDocumentTypes().stream().sorted(Comparator.naturalOrder()).toList();
    }
}
