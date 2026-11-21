package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.dto.ResolvedSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryResolver;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateSigner;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateImagePurpose;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateImage;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class CertificateTemplateSignatories {

    static final SignatoryDocumentType DOCUMENT_TYPE = SignatoryDocumentType.GRADUATION_APPROVAL;

    private final CertificateSignatoryResolver resolver;
    private final CertificateTemplateImageRepository imageRepository;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CertificateTemplateSignatories(CertificateSignatoryResolver resolver, CertificateTemplateImageRepository imageRepository,
                                          CurrentActorFinder currentActorFinder, Clock clock) {
        this.resolver = resolver;
        this.imageRepository = imageRepository;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public CertificateTemplateContent applyTo(Long templateId, CertificateTemplateContent content) {
        resolver.requireDistinct(content.signatory1Id(), content.signatory2Id());
        ResolvedSignatory first = resolver.selectFor(content.signatory1Id(), DOCUMENT_TYPE);
        ResolvedSignatory second = resolver.selectFor(content.signatory2Id(), DOCUMENT_TYPE);
        return new CertificateTemplateContent(content.instituteName(), content.logoImageId(), content.title(), content.introText(),
                content.approvalText(), content.dictationText(), content.qrLabel(), content.footerLegend(),
                snapshotOf(templateId, first), snapshotOf(templateId, second), content.signatory1Id(), content.signatory2Id());
    }

    private CertificateTemplateSigner snapshotOf(Long templateId, ResolvedSignatory signatory) {
        if (signatory == null) {
            return null;
        }
        Long imageId = signatory.signature() == null ? null : copyImage(templateId, signatory.signature());
        return new CertificateTemplateSigner(signatory.name(), signatory.role(), imageId);
    }

    private Long copyImage(Long templateId, ResolvedSignatory.SignatureImage signature) {
        return imageRepository.findFirstByTemplateIdAndPurposeAndSha256OrderByIdDesc(templateId, TemplateImagePurpose.SIGNATURE, signature.sha256())
                .map(CertificateTemplateImage::getId)
                .orElseGet(() -> imageRepository.saveAndFlush(CertificateTemplateImage.builder()
                        .templateId(templateId)
                        .purpose(TemplateImagePurpose.SIGNATURE)
                        .contentType(signature.contentType())
                        .sizeBytes(signature.sizeBytes())
                        .widthPixels(signature.widthPixels())
                        .heightPixels(signature.heightPixels())
                        .sha256(signature.sha256())
                        .content(signature.content())
                        .uploadedAt(LocalDateTime.now(clock))
                        .uploadedById(currentActorFinder.currentPersonId())
                        .build()).getId());
    }
}
