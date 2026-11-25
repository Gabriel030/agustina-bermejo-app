package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedTemplateGenericRouteTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final CertificateCode CODE = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateDigest digest = new CertificateDigest();
    private final CertificateTemplateDefinition previewDefinition = new BuiltInCertificateTemplate()
            .resolve(com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind.PROGRAM, null);
    private final CertificateTemplateDefinition regularDefinition = new CertificateTemplateDefinition("REGULAR_BUILT_IN_V1",
            PageFormat.A4_LANDSCAPE, "CONSTANCIA DE ALUMNO REGULAR", "Se certifica que {holder} es alumno/a regular.",
            new Rect(100, 150, 642, 280), new Rect(690, 60, 90, 90), new Rect(430, 60, 250, 40), new Rect(100, 60, 260, 80));

    private final CertificateAssets assets = new CertificateAssets(null, List.of(), "0000007/2026",
            new CertificateTexts("INSTITUTO DE PRUEBA", "CONSTANCIA {recipient}", "Se deja constancia de que",
                    "promedio {average} y avance {completionPercent} en {subjectName} el {examDate}", "Dictada por la institución",
                    "QR {registryNumber}", "Emitido el {issuedOn}"));

    private CertificateSnapshot snapshot(String templateRef) {
        return new CertificateSnapshot("PÉREZ, ANA", "DNI", "30123456", "la carrera", "Tecnicatura en Enfermería", null, 120,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto de prueba",
                List.of(new SignatorySnapshot("Ana Firmante", "Rectora"), new SignatorySnapshot("Luis Firmante", "Secretario")),
                templateRef, null,
                Map.of("recipient", "ANTE QUIEN CORRESPONDA", "average", "8,50", "completionPercent", "75%",
                        "subjectName", "Anatomía", "examDate", "02/12/2026"));
    }

    private String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void genericRouteWithPublishedVersionExpandsEveryAllowedMarkerAndUsesVersionTexts() throws Exception {
        String text = textOf(renderer.render(regularDefinition, snapshot("TEMPLATE_V3"), CODE, URL, assets));

        assertThat(text).contains("INSTITUTO DE PRUEBA", "CONSTANCIA ANTE QUIEN CORRESPONDA", "Se deja constancia de que",
                "promedio 8,50 y avance 75% en Anatomía el 02/12/2026", "QR 0000007/2026", "Emitido el 05/11/2026",
                "Ana Firmante", "Luis Firmante");
        assertThat(text).doesNotContain("{").doesNotContain("CONSTANCIA DE ALUMNO REGULAR");
    }

    @Test
    void emittedGenericRouteIsByteIdenticalToThePreviewRoute() {
        byte[] emitted = renderer.render(regularDefinition, snapshot("TEMPLATE_V3"), CODE, URL, assets);
        byte[] preview = renderer.render(previewDefinition, snapshot("TEMPLATE_V3"), CODE, URL, assets);

        assertThat(digest.sha256(emitted)).isEqualTo(digest.sha256(preview));
    }

    @Test
    void sameSnapshotAndPublishedVersionRenderTheSameSha256() {
        String first = digest.sha256(renderer.render(regularDefinition, snapshot("TEMPLATE_V3"), CODE, URL, assets));
        String second = digest.sha256(renderer.render(regularDefinition, snapshot("TEMPLATE_V3"), CODE, URL, assets));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void legacySnapshotWithoutPublishedVersionKeepsTheLegacyGenericRoute() throws Exception {
        String text = textOf(renderer.render(regularDefinition, snapshot("REGULAR_BUILT_IN_V1"), CODE, URL));

        assertThat(text).contains("CONSTANCIA DE ALUMNO REGULAR", "es alumno/a regular.");
    }
}
