package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateNothingToDiscardException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateTemplateDraftDiscarder {

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;

    public CertificateTemplateDraftDiscarder(CertificateTemplateFinder templateFinder,
                                             CertificateTemplateVersionRepository versionRepository) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public void discard(GraduationCertificateKind kind) {
        discard(templateFinder.findForUpdate(kind));
    }

    @Transactional
    public void discard(DocumentTemplateType type) {
        discard(templateFinder.findForUpdate(type));
    }

    private void discard(CertificateTemplate template) {
        CertificateTemplateVersion draft = versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .orElseThrow(TemplateNothingToDiscardException::new);
        versionRepository.delete(draft);
        versionRepository.flush();
    }
}
