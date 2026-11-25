package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateAssets;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTexts;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateSigner;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateVersionNotFoundException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class CertificateTemplateRenderSource {

    private static final String BLANK = " ";

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateImageRepository imageRepository;

    public CertificateTemplateRenderSource(CertificateTemplateFinder templateFinder,
                                           CertificateTemplateVersionRepository versionRepository,
                                           CertificateTemplateImageRepository imageRepository) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
        this.imageRepository = imageRepository;
    }

    public CertificateTemplateVersion currentPublished(GraduationCertificateKind kind) {
        CertificateTemplate template = templateFinder.find(kind);
        return versionRepository.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .orElseThrow(TemplateVersionNotFoundException::new);
    }

    public CertificateTemplateVersion currentPublished(DocumentTemplateType type) {
        CertificateTemplate template = templateFinder.find(type);
        return versionRepository.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .orElseThrow(TemplateVersionNotFoundException::new);
    }

    public Optional<CertificateTemplateVersion> findCurrentPublished(DocumentTemplateType type) {
        CertificateTemplate template = templateFinder.find(type);
        return versionRepository.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED);
    }

    public CertificateTemplateVersion forPreview(DocumentTemplateType type, TemplatePreviewSource source) {
        if (source == TemplatePreviewSource.PUBLISHED) {
            return currentPublished(type);
        }
        CertificateTemplate template = templateFinder.find(type);
        return versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .orElseThrow(TemplateVersionNotFoundException::new);
    }

    public CertificateTemplateVersion byId(Long versionId) {
        return versionRepository.findById(versionId).orElseThrow(TemplateVersionNotFoundException::new);
    }

    public CertificateTemplateVersion forPreview(GraduationCertificateKind kind, TemplatePreviewSource source) {
        if (source == TemplatePreviewSource.PUBLISHED) {
            return currentPublished(kind);
        }
        CertificateTemplate template = templateFinder.find(kind);
        return versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .orElseThrow(TemplateVersionNotFoundException::new);
    }

    public CertificateSnapshot snapshotWith(CertificateSnapshot snapshot, CertificateTemplateVersion version) {
        CertificateTemplateContent content = version.content();
        String templateRef = version.getVersionNumber() == null ? "TEMPLATE_DRAFT" : "TEMPLATE_V" + version.getVersionNumber();
        return new CertificateSnapshot(snapshot.holderFullName(), snapshot.documentTypeLabel(), snapshot.documentNumber(),
                snapshot.titleKind(), snapshot.titleName(), snapshot.programLevel(), snapshot.hours(), snapshot.graduationDate(),
                snapshot.issuedOn(),
                content.instituteName() == null ? snapshot.institutionName() : content.instituteName(),
                List.of(signatoryOf(content.firstSigner()), signatoryOf(content.secondSigner())), templateRef, null,
                snapshot.extraMarkers());
    }

    public CertificateAssets assetsOf(CertificateTemplateVersion version, String registryNumber) {
        CertificateTemplateContent content = version.content();
        Long templateId = version.getTemplateId();
        List<byte[]> signatures = new ArrayList<>();
        signatures.add(imageOf(templateId, content.firstSigner() == null ? null : content.firstSigner().signatureImageId()));
        signatures.add(imageOf(templateId, content.secondSigner() == null ? null : content.secondSigner().signatureImageId()));
        CertificateTexts texts = new CertificateTexts(content.instituteName(), content.title(), content.introText(),
                content.approvalText(), content.dictationText(), content.qrLabel(), content.footerLegend());
        return new CertificateAssets(imageOf(templateId, content.logoImageId()), signatures, registryNumber, texts);
    }

    private SignatorySnapshot signatoryOf(CertificateTemplateSigner signer) {
        return signer == null
                ? new SignatorySnapshot(BLANK, BLANK)
                : new SignatorySnapshot(signer.name() == null ? BLANK : signer.name(), signer.role() == null ? BLANK : signer.role());
    }

    private byte[] imageOf(Long templateId, Long imageId) {
        return imageId == null ? null : imageRepository.findByIdAndTemplateId(imageId, templateId)
                .map(image -> image.getContent()).orElse(null);
    }
}
