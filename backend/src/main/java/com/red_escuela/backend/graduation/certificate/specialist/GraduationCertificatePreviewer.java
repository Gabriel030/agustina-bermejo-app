package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateResolver;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.preview.PreviewWatermarker;
import com.red_escuela.backend.graduation.certificate.source.GraduationSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class GraduationCertificatePreviewer {

    private final Map<GraduationCertificateKind, GraduationSource> sources = new EnumMap<>(GraduationCertificateKind.class);
    private final CertificateTemplateResolver templateResolver;
    private final CertificatePdfRenderer renderer;
    private final CertificateVerificationUrl verificationUrl;
    private final PreviewWatermarker watermarker;
    private final SecureRandom random = new SecureRandom();

    public GraduationCertificatePreviewer(List<GraduationSource> graduationSources, CertificateTemplateResolver templateResolver,
                                          CertificatePdfRenderer renderer, CertificateVerificationUrl verificationUrl,
                                          PreviewWatermarker watermarker) {
        graduationSources.forEach(source -> sources.put(source.kind(), source));
        this.templateResolver = templateResolver;
        this.renderer = renderer;
        this.verificationUrl = verificationUrl;
        this.watermarker = watermarker;
    }

    @Transactional(readOnly = true)
    public GraduationCertificatePdf preview(GraduationCertificateKind kind, Long enrollmentId) {
        GraduationSource source = sources.get(kind);
        source.requireEligible(enrollmentId);
        CertificateSnapshot snapshot = source.snapshotOf(enrollmentId);
        CertificateCode code = CertificateCode.generate(random);
        byte[] pdf = renderer.render(templateResolver.resolve(kind, source.titleIdOf(enrollmentId)), snapshot, code,
                verificationUrl.of(code));
        return new GraduationCertificatePdf(code.value(), watermarker.apply(pdf));
    }
}
