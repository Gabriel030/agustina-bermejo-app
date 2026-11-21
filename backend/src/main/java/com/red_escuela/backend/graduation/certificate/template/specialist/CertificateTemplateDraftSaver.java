package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateTemplateDraftSaver {

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateContentRules rules;
    private final CertificateTemplateImageReferences imageReferences;
    private final CertificateTemplateSignatories signatories;
    private final Clock clock;

    public CertificateTemplateDraftSaver(CertificateTemplateFinder templateFinder, CertificateTemplateVersionRepository versionRepository,
                                         CertificateTemplateContentRules rules, CertificateTemplateImageReferences imageReferences,
                                         CertificateTemplateSignatories signatories, Clock clock) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
        this.rules = rules;
        this.imageReferences = imageReferences;
        this.signatories = signatories;
        this.clock = clock;
    }

    @Transactional
    public CertificateTemplateVersionResponse save(GraduationCertificateKind kind, CertificateTemplateContent requested) {
        CertificateTemplateContent normalized = rules.normalize(requested);
        CertificateTemplateContent content = normalized;
        rules.requireKnownMarkers(content);
        CertificateTemplate template = templateFinder.findForUpdate(kind);
        imageReferences.requireExisting(template.getId(), normalized);
        content = signatories.applyTo(template.getId(), normalized);
        LocalDateTime now = LocalDateTime.now(clock);
        CertificateTemplateVersion draft = versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .orElse(null);
        if (draft == null) {
            Integer basedOn = versionRepository
                    .findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                    .map(CertificateTemplateVersion::getVersionNumber).orElse(null);
            draft = CertificateTemplateVersion.draftOf(template.getId(), basedOn, content, now);
        } else {
            draft.replaceContent(content, now);
        }
        return CertificateTemplateResponses.of(versionRepository.saveAndFlush(draft));
    }
}
