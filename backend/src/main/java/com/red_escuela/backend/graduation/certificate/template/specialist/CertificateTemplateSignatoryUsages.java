package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryTemplateUsageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateRepository;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class CertificateTemplateSignatoryUsages {

    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateRepository templateRepository;

    public CertificateTemplateSignatoryUsages(CertificateTemplateVersionRepository versionRepository,
                                              CertificateTemplateRepository templateRepository) {
        this.versionRepository = versionRepository;
        this.templateRepository = templateRepository;
    }

    public Map<Long, List<SignatoryTemplateUsageResponse>> usagesOf(Collection<Long> signatoryIds) {
        Map<Long, List<SignatoryTemplateUsageResponse>> result = new HashMap<>();
        if (signatoryIds.isEmpty()) {
            return result;
        }
        List<CertificateTemplateVersion> versions = versionRepository.findUsingSignatories(signatoryIds);
        Map<Long, CertificateTemplate> templates = templateRepository.findAllById(
                        versions.stream().map(CertificateTemplateVersion::getTemplateId).distinct().toList()).stream()
                .collect(Collectors.toMap(CertificateTemplate::getId, Function.identity()));
        List<CertificateTemplateVersion> ordered = versions.stream()
                .sorted(Comparator.comparing(CertificateTemplateVersion::getTemplateId)
                        .thenComparing(version -> version.getVersionNumber() == null ? Integer.MAX_VALUE : version.getVersionNumber()))
                .toList();
        for (Long signatoryId : signatoryIds) {
            for (CertificateTemplateVersion version : ordered) {
                if (signatoryId.equals(version.getSignatory1Id()) || signatoryId.equals(version.getSignatory2Id())) {
                    result.computeIfAbsent(signatoryId, key -> new ArrayList<>()).add(new SignatoryTemplateUsageResponse(
                            documentTypeOf(templates.get(version.getTemplateId())), nameOf(templates.get(version.getTemplateId())),
                            version.getVersionNumber(), version.getStatus() == null ? TemplateVersionStatus.DRAFT : version.getStatus()));
                }
            }
        }
        return result;
    }

    private SignatoryDocumentType documentTypeOf(CertificateTemplate template) {
        return template.getDocumentType() == null
                ? CertificateTemplateSignatories.DOCUMENT_TYPE
                : SignatoryDocumentType.valueOf(template.getDocumentType().name());
    }

    private String nameOf(CertificateTemplate template) {
        if (template.getDocumentType() != null) {
            return switch (template.getDocumentType()) {
                case REGULAR_STUDENT_CERTIFICATE -> "Constancia de alumno regular";
                case APPROVED_SUBJECTS_CERTIFICATE -> "Constancia de materias aprobadas";
                case EXAM_REGISTRATION_CERTIFICATE -> "Constancia de inscripción a examen";
                case EXAM_CERTIFICATE -> "Constancia de examen rendido";
            };
        }
        return template.getKind() == GraduationCertificateKind.PROGRAM
                ? "Certificado de aprobación de tecnicatura"
                : "Certificado de aprobación de capacitación";
    }
}
