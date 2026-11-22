package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CertificatePdfDeterminismTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateDigest digest = new CertificateDigest();
    private final CertificateCode code = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");

    private CertificateSnapshot snapshotOf(String holder) {
        return new CertificateSnapshot(holder, "DNI", "30123456", "la carrera", "Tecnicatura en Enfermería", "UNDERGRADUATE",
                1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto de prueba",
                List.of(new SignatorySnapshot("Dirección", "Dirección académica")), BuiltInCertificateTemplate.BUILT_IN_V1);
    }

    @Test
    void sameSnapshotRendersTheSameSha256WithTheApprovalDesign() {
        CertificateTemplateDefinition template = new BuiltInCertificateTemplate().resolve(GraduationCertificateKind.PROGRAM, 1L);
        String first = digest.sha256(renderer.render(template, snapshotOf("PÉREZ, ANA"), code, URL));
        await().pollDelay(Duration.ofMillis(1100)).until(() -> true);
        String second = digest.sha256(renderer.render(template, snapshotOf("PÉREZ, ANA"), code, URL));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentSnapshotRendersADifferentSha256() {
        CertificateTemplateDefinition template = new BuiltInCertificateTemplate().resolve(GraduationCertificateKind.PROGRAM, 1L);
        String first = digest.sha256(renderer.render(template, snapshotOf("PÉREZ, ANA"), code, URL));
        String second = digest.sha256(renderer.render(template, snapshotOf("GÓMEZ, LUIS"), code, URL));
        assertThat(second).isNotEqualTo(first);
    }
}
