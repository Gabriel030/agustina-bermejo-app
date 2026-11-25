package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.preview.PreviewWatermarker;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class CertificateTemplatePreviewer {

    private static final DateTimeFormatter EXAM_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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
        return render(kind, renderSource.forPreview(kind, source), sampleSnapshot(kind));
    }

    public GraduationCertificatePdf preview(DocumentTemplateType type, TemplatePreviewSource source) {
        return render(GraduationCertificateKind.PROGRAM, renderSource.forPreview(type, source), sampleDocumentSnapshot(type));
    }

    private GraduationCertificatePdf render(GraduationCertificateKind kind, CertificateTemplateVersion version, CertificateSnapshot sample) {
        CertificateCode code = CertificateCode.generate(random);
        CertificateSnapshot snapshot = renderSource.snapshotWith(sample, version);
        byte[] pdf = renderer.render(builtInTemplate.resolve(kind, null), snapshot, code, verificationUrl.of(code),
                renderSource.assetsOf(version, sampleRegistryNumber()));
        return new GraduationCertificatePdf(code.value(), watermark.apply(pdf));
    }

    private CertificateSnapshot sampleDocumentSnapshot(DocumentTemplateType type) {
        LocalDate today = LocalDate.now(clock);
        String subject = switch (type) {
            case REGULAR_STUDENT_CERTIFICATE, APPROVED_SUBJECTS_CERTIFICATE -> "Nombre de la carrera de ejemplo";
            case EXAM_REGISTRATION_CERTIFICATE, EXAM_CERTIFICATE -> "Nombre de la materia de ejemplo";
        };
        return new CertificateSnapshot(
                "APELLIDO, NOMBRE DE EJEMPLO",
                "DNI",
                "12345678",
                "la Tecnicatura en",
                subject,
                null,
                null,
                today,
                today,
                DEFAULT_INSTITUTION,
                List.of(),
                BuiltInCertificateTemplate.BUILT_IN_V1, null, sampleExtraMarkers(type));
    }

    private Map<String, String> sampleExtraMarkers(DocumentTemplateType type) {
        return switch (type) {
            case REGULAR_STUDENT_CERTIFICATE -> Map.of("recipient", "A quien corresponda");
            case APPROVED_SUBJECTS_CERTIFICATE -> Map.of("average", "8,50", "completionPercent", "75");
            case EXAM_REGISTRATION_CERTIFICATE, EXAM_CERTIFICATE -> Map.of("recipient", "A quien corresponda",
                    "subjectName", "Nombre de la materia de ejemplo", "examDate", LocalDate.now(clock).format(EXAM_DATE_FORMAT));
        };
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
