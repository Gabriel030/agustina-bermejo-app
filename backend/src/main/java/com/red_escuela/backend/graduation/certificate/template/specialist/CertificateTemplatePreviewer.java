package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.preview.PreviewWatermarker;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CertificateTemplatePreviewer {

    private static final String DEFAULT_INSTITUTION = "INSTITUTO AGUSTINA BERMEJO";

    private final CertificateTemplateRenderSource renderSource;
    private final BuiltInCertificateTemplate builtInTemplate;
    private final CertificatePdfRenderer renderer;
    private final CertificateVerificationUrl verificationUrl;
    private final PreviewWatermarker watermark;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CertificateTemplatePreviewer(CertificateTemplateRenderSource renderSource, BuiltInCertificateTemplate builtInTemplate,
                                        CertificatePdfRenderer renderer, CertificateVerificationUrl verificationUrl,
                                        PreviewWatermarker watermark, Clock clock) {
        this.renderSource = renderSource;
        this.builtInTemplate = builtInTemplate;
        this.renderer = renderer;
        this.verificationUrl = verificationUrl;
        this.watermark = watermark;
        this.clock = clock;
    }

    public GraduationCertificatePdf preview(GraduationCertificateKind kind, TemplatePreviewSource source) {
        CertificateTemplateVersion version = renderSource.forPreview(kind, source);
        CertificateCode code = CertificateCode.generate(random);
        CertificateSnapshot snapshot = renderSource.snapshotWith(sampleSnapshot(kind), version);
        byte[] pdf = renderer.render(builtInTemplate.resolve(kind, null), snapshot, code, verificationUrl.of(code),
                renderSource.assetsOf(version, sampleRegistryNumber()));
        return new GraduationCertificatePdf(code.value(), watermark.apply(pdf));
    }

    private String sampleRegistryNumber() {
        return String.format("%07d/%d", 0, LocalDate.now(clock).getYear());
    }

    private CertificateSnapshot sampleSnapshot(GraduationCertificateKind kind) {
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
                DEFAULT_INSTITUTION,
                List.of(),
                BuiltInCertificateTemplate.BUILT_IN_V1, null);
    }
}
