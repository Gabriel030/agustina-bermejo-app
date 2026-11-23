package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfEngineGoldenTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final String CHANGED_WITHOUT_VERSION_BUMP = "El render del PDF cambió. Si el cambio es intencional, subí "
            + "CertificatePdfRenderer.ENGINE_VERSION y agregá la entrada de la nueva versión con el SHA-256 actual en "
            + "GOLDEN_SHA256_BY_ENGINE_VERSION (no reemplaces la de la versión anterior si todavía hay emisiones con ella). "
            + "Si no era intencional, revertí el cambio: las regeneraciones dejarían de coincidir con el hash emitido.";

    private static final Map<String, Map<String, String>> GOLDEN_SHA256_BY_ENGINE_VERSION = Map.of(
            "1", Map.of(
                    "approval", "f70242244b778a25217f0b0a0b9d9019fd21007826cb95cdfc0f37868febace1",
                    "regular", "520b67dd33397b457083919d261387ff6f10790ff90c4411a9482b6f7e6d64c9"));

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateDigest digest = new CertificateDigest();
    private final CertificateCode code = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");
    private final CertificateSnapshot snapshot = new CertificateSnapshot("PÉREZ, ANA", "DNI", "30123456", "la carrera",
            "Tecnicatura en Enfermería", "UNDERGRADUATE", 1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5),
            "Instituto de prueba", List.of(new SignatorySnapshot("Dirección", "Dirección académica")),
            BuiltInCertificateTemplate.BUILT_IN_V1, null);

    @Test
    void approvalDesignMatchesTheGoldenHashOfTheCurrentEngineVersion() {
        CertificateTemplateDefinition template = new BuiltInCertificateTemplate().resolve(GraduationCertificateKind.PROGRAM, 1L);

        assertThat(digest.sha256(renderer.render(template, snapshot, code, URL)))
                .as(CHANGED_WITHOUT_VERSION_BUMP)
                .isEqualTo(golden("approval"));
    }

    @Test
    void regularTemplateMatchesTheGoldenHashOfTheCurrentEngineVersion() {
        CertificateTemplateDefinition template = new CertificateTemplateDefinition("REGULAR_BUILT_IN_V1", PageFormat.A4_LANDSCAPE,
                "CONSTANCIA DE ALUMNO REGULAR", "Se certifica que {holder} es alumno/a regular.",
                new Rect(100, 150, 642, 280), new Rect(690, 60, 90, 90), new Rect(430, 60, 250, 40), new Rect(100, 60, 260, 80));

        assertThat(digest.sha256(renderer.render(template, snapshot, code, URL)))
                .as(CHANGED_WITHOUT_VERSION_BUMP)
                .isEqualTo(golden("regular"));
    }

    private String golden(String design) {
        Map<String, String> byDesign = GOLDEN_SHA256_BY_ENGINE_VERSION.get(CertificatePdfRenderer.ENGINE_VERSION);
        assertThat(byDesign)
                .as("Falta la entrada golden de la versión de motor " + CertificatePdfRenderer.ENGINE_VERSION)
                .isNotNull();
        return byDesign.get(design);
    }
}
