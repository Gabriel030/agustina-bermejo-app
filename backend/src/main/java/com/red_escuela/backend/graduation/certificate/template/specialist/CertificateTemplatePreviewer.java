package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateAssets;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateSigner;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateVersionNotFoundException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CertificateTemplatePreviewer {

    private static final String DEFAULT_INSTITUTION = "INSTITUTO AGUSTINA BERMEJO";

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateImageRepository imageRepository;
    private final BuiltInCertificateTemplate builtInTemplate;
    private final CertificatePdfRenderer renderer;
    private final CertificateVerificationUrl verificationUrl;
    private final PreviewWatermark watermark;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CertificateTemplatePreviewer(CertificateTemplateFinder templateFinder, CertificateTemplateVersionRepository versionRepository,
                                        CertificateTemplateImageRepository imageRepository, BuiltInCertificateTemplate builtInTemplate,
                                        CertificatePdfRenderer renderer, CertificateVerificationUrl verificationUrl,
                                        PreviewWatermark watermark, Clock clock) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
        this.imageRepository = imageRepository;
        this.builtInTemplate = builtInTemplate;
        this.renderer = renderer;
        this.verificationUrl = verificationUrl;
        this.watermark = watermark;
        this.clock = clock;
    }

    public GraduationCertificatePdf preview(GraduationCertificateKind kind, TemplatePreviewSource source) {
        CertificateTemplate template = templateFinder.find(kind);
        CertificateTemplateVersion version = source == TemplatePreviewSource.DRAFT
                ? versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                        .orElseThrow(TemplateVersionNotFoundException::new)
                : versionRepository.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                        .orElseThrow(TemplateVersionNotFoundException::new);
        CertificateTemplateContent content = version.content();
        CertificateCode code = CertificateCode.generate(random);
        byte[] pdf = renderer.render(builtInTemplate.resolve(kind, null), sampleSnapshot(kind, content), code,
                verificationUrl.of(code), assetsOf(template.getId(), content));
        return new GraduationCertificatePdf(code.value(), watermark.stamp(pdf));
    }

    private CertificateSnapshot sampleSnapshot(GraduationCertificateKind kind, CertificateTemplateContent content) {
        LocalDate today = LocalDate.now(clock);
        boolean program = kind == GraduationCertificateKind.PROGRAM;
        return new CertificateSnapshot(
                "APELLIDO, NOMBRE DE EJEMPLO",
                "DNI",
                "12345678",
                program ? "la Tecnicatura en" : "la Capacitación en",
                program ? "Nombre de la carrera de ejemplo" : "Nombre del curso de ejemplo",
                null,
                120,
                today,
                today,
                content.instituteName() == null ? DEFAULT_INSTITUTION : content.instituteName(),
                List.of(signatoryOf(content.firstSigner()), signatoryOf(content.secondSigner())),
                BuiltInCertificateTemplate.BUILT_IN_V1);
    }

    private SignatorySnapshot signatoryOf(CertificateTemplateSigner signer) {
        return signer == null
                ? new SignatorySnapshot(" ", " ")
                : new SignatorySnapshot(signer.name() == null ? " " : signer.name(), signer.role() == null ? " " : signer.role());
    }

    private CertificateAssets assetsOf(Long templateId, CertificateTemplateContent content) {
        List<byte[]> signatures = new ArrayList<>();
        signatures.add(imageOf(templateId, content.firstSigner() == null ? null : content.firstSigner().signatureImageId()));
        signatures.add(imageOf(templateId, content.secondSigner() == null ? null : content.secondSigner().signatureImageId()));
        return new CertificateAssets(imageOf(templateId, content.logoImageId()), signatures, null);
    }

    private byte[] imageOf(Long templateId, Long imageId) {
        return imageId == null ? null : imageRepository.findByIdAndTemplateId(imageId, templateId)
                .map(image -> image.getContent()).orElse(null);
    }
}
