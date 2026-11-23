package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateImage;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateImageNotFoundException;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateVersionNotFoundException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CertificateTemplateReader {

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateImageRepository imageRepository;

    public CertificateTemplateReader(CertificateTemplateFinder templateFinder, CertificateTemplateVersionRepository versionRepository,
                                     CertificateTemplateImageRepository imageRepository) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
        this.imageRepository = imageRepository;
    }

    public DocumentTemplateResponse read(DocumentTemplateType type) {
        CertificateTemplate template = templateFinder.find(type);
        CertificateTemplateVersionResponse published = versionRepository
                .findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .map(CertificateTemplateResponses::of).orElse(null);
        CertificateTemplateVersionResponse draft = versionRepository
                .findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .map(CertificateTemplateResponses::of).orElse(null);
        return new DocumentTemplateResponse(type, draft, published, draft != null);
    }

    public CertificateTemplateImageContent readImage(DocumentTemplateType type, Long imageId) {
        return imageOf(templateFinder.find(type), imageId);
    }

    public CertificateTemplateResponse read(GraduationCertificateKind kind) {
        CertificateTemplate template = templateFinder.find(kind);
        CertificateTemplateVersionResponse published = versionRepository
                .findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .map(CertificateTemplateResponses::of).orElse(null);
        CertificateTemplateVersionResponse draft = versionRepository
                .findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .map(CertificateTemplateResponses::of).orElse(null);
        return new CertificateTemplateResponse(kind, published, draft, draft != null);
    }

    public List<CertificateTemplateVersionSummaryResponse> listVersions(GraduationCertificateKind kind) {
        CertificateTemplate template = templateFinder.find(kind);
        return versionRepository.findByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .stream().map(CertificateTemplateResponses::summaryOf).toList();
    }

    public CertificateTemplateVersionResponse readVersion(GraduationCertificateKind kind, int versionNumber) {
        CertificateTemplate template = templateFinder.find(kind);
        CertificateTemplateVersion version = versionRepository.findByTemplateIdAndVersionNumber(template.getId(), versionNumber)
                .orElseThrow(TemplateVersionNotFoundException::new);
        return CertificateTemplateResponses.of(version);
    }

    public CertificateTemplateImageContent readImage(GraduationCertificateKind kind, Long imageId) {
        return imageOf(templateFinder.find(kind), imageId);
    }

    private CertificateTemplateImageContent imageOf(CertificateTemplate template, Long imageId) {
        CertificateTemplateImage image = imageRepository.findByIdAndTemplateId(imageId, template.getId())
                .orElseThrow(TemplateImageNotFoundException::new);
        return new CertificateTemplateImageContent(image.getContentType(), image.getContent());
    }
}
