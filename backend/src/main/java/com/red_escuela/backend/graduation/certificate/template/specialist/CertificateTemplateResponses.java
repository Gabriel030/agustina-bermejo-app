package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateImage;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;

final class CertificateTemplateResponses {

    private CertificateTemplateResponses() {
    }

    static CertificateTemplateVersionResponse of(CertificateTemplateVersion version) {
        return new CertificateTemplateVersionResponse(version.getId(), version.getVersionNumber(), version.getStatus(),
                version.getBasedOnVersionNumber(), version.content(), version.getUpdatedAt(), version.getPublishedAt(),
                version.getPublishedByName());
    }

    static CertificateTemplateVersionSummaryResponse summaryOf(CertificateTemplateVersion version) {
        return new CertificateTemplateVersionSummaryResponse(version.getVersionNumber(), version.getPublishedAt(),
                version.getPublishedByName());
    }

    static CertificateTemplateImageResponse of(CertificateTemplateImage image) {
        return new CertificateTemplateImageResponse(image.getId(), image.getPurpose(), image.getContentType(), image.getSizeBytes(),
                image.getWidthPixels(), image.getHeightPixels());
    }
}
