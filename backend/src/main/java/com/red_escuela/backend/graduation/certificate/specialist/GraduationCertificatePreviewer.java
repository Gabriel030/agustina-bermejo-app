package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.preview.PreviewWatermarker;
import com.red_escuela.backend.graduation.certificate.source.GraduationSource;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class GraduationCertificatePreviewer {

    private final Map<GraduationCertificateKind, GraduationSource> sources = new EnumMap<>(GraduationCertificateKind.class);
    private final BuiltInCertificateTemplate builtInTemplate;
    private final CertificateTemplateRenderSource templateSource;
    private final Clock clock;
    private final CertificatePdfRenderer renderer;
    private final CertificateVerificationUrl verificationUrl;
    private final PreviewWatermarker watermarker;
    private final SecureRandom random = new SecureRandom();

    public GraduationCertificatePreviewer(List<GraduationSource> graduationSources, BuiltInCertificateTemplate builtInTemplate, CertificateTemplateRenderSource templateSource, Clock clock,
                                          CertificatePdfRenderer renderer, CertificateVerificationUrl verificationUrl,
                                          PreviewWatermarker watermarker) {
        graduationSources.forEach(source -> sources.put(source.kind(), source));
        this.builtInTemplate = builtInTemplate;
        this.templateSource = templateSource;
        this.clock = clock;
        this.renderer = renderer;
        this.verificationUrl = verificationUrl;
        this.watermarker = watermarker;
    }

    @Transactional(readOnly = true)
    public GraduationCertificatePdf preview(GraduationCertificateKind kind, Long enrollmentId) {
        GraduationSource source = sources.get(kind);
        source.requireEligible(enrollmentId);
        CertificateTemplateVersion version = templateSource.currentPublished(kind);
        CertificateSnapshot snapshot = templateSource.snapshotWith(source.snapshotOf(enrollmentId), version);
        CertificateCode code = CertificateCode.generate(random);
        String placeholderRegistry = String.format("%07d/%d", 0, LocalDate.now(clock).getYear());
        byte[] pdf = renderer.render(builtInTemplate.resolve(kind, null), snapshot, code, verificationUrl.of(code),
                templateSource.assetsOf(version, placeholderRegistry));
        return new GraduationCertificatePdf(code.value(), watermarker.apply(pdf));
    }
}
