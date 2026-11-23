package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateImagePurpose;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateImage;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateImageInvalidException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class CertificateTemplateImageUploader {

    private static final String PNG_CONTENT_TYPE = "image/png";

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateImageRepository imageRepository;
    private final TemplatePngInspector pngInspector;
    private final CertificateDigest digest;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CertificateTemplateImageUploader(CertificateTemplateFinder templateFinder, CertificateTemplateImageRepository imageRepository,
                                            TemplatePngInspector pngInspector, CertificateDigest digest,
                                            CurrentActorFinder currentActorFinder, Clock clock) {
        this.templateFinder = templateFinder;
        this.imageRepository = imageRepository;
        this.pngInspector = pngInspector;
        this.digest = digest;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public CertificateTemplateImageResponse upload(GraduationCertificateKind kind, CertificateTemplateImageUpload upload) {
        return store(upload, () -> templateFinder.find(kind));
    }

    @Transactional
    public CertificateTemplateImageResponse upload(DocumentTemplateType type, CertificateTemplateImageUpload upload) {
        return store(upload, () -> templateFinder.find(type));
    }

    private CertificateTemplateImageResponse store(CertificateTemplateImageUpload upload, Supplier<CertificateTemplate> templateSupplier) {
        if (upload.purpose() == null) {
            throw new TemplateImageInvalidException();
        }
        TemplatePngInspector.PngFacts facts = pngInspector.inspect(upload.content());
        if (upload.purpose() == TemplateImagePurpose.SIGNATURE && !facts.hasAlpha()) {
            throw new TemplateImageInvalidException();
        }
        CertificateTemplate template = templateSupplier.get();
        CertificateTemplateImage image = imageRepository.saveAndFlush(CertificateTemplateImage.builder()
                .templateId(template.getId())
                .purpose(upload.purpose())
                .contentType(PNG_CONTENT_TYPE)
                .sizeBytes(upload.content().length)
                .widthPixels(facts.widthPixels())
                .heightPixels(facts.heightPixels())
                .sha256(digest.sha256(upload.content()))
                .content(upload.content())
                .uploadedAt(LocalDateTime.now(clock))
                .uploadedById(currentActorFinder.currentPersonId())
                .build());
        return CertificateTemplateResponses.of(image);
    }
}
