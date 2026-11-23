package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfDeterminismTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateDigest digest = new CertificateDigest();
    private final CertificateCode code = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");
    private final CertificateTemplateDefinition approvalTemplate = new BuiltInCertificateTemplate()
            .resolve(GraduationCertificateKind.PROGRAM, 1L);
    private final CertificateTemplateDefinition regularTemplate = new CertificateTemplateDefinition("REGULAR_BUILT_IN_V1",
            PageFormat.A4_LANDSCAPE, "CONSTANCIA DE ALUMNO REGULAR", "Se certifica que {holder} es alumno/a regular.",
            new Rect(100, 150, 642, 280), new Rect(690, 60, 90, 90), new Rect(430, 60, 250, 40), new Rect(100, 60, 260, 80));

    private CertificateSnapshot snapshotOf(String holder) {
        return new CertificateSnapshot(holder, "DNI", "30123456", "la carrera", "Tecnicatura en Enfermería", "UNDERGRADUATE",
                1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto de prueba",
                List.of(new SignatorySnapshot("Dirección", "Dirección académica")), BuiltInCertificateTemplate.BUILT_IN_V1);
    }

    @Test
    void sameSnapshotRendersTheSameSha256WithTheApprovalDesign() {
        String first = digest.sha256(renderer.render(approvalTemplate, snapshotOf("PÉREZ, ANA"), code, URL));
        String second = digest.sha256(renderer.render(approvalTemplate, snapshotOf("PÉREZ, ANA"), code, URL));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameSnapshotRendersTheSameSha256WithTheRegularTemplate() {
        String first = digest.sha256(renderer.render(regularTemplate, snapshotOf("PÉREZ, ANA"), code, URL));
        String second = digest.sha256(renderer.render(regularTemplate, snapshotOf("PÉREZ, ANA"), code, URL));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentSnapshotRendersADifferentSha256() {
        String first = digest.sha256(renderer.render(approvalTemplate, snapshotOf("PÉREZ, ANA"), code, URL));
        String second = digest.sha256(renderer.render(approvalTemplate, snapshotOf("GÓMEZ, LUIS"), code, URL));
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void renderedPdfCarriesNoClockDependentMetadata() throws Exception {
        for (CertificateTemplateDefinition template : List.of(approvalTemplate, regularTemplate)) {
            byte[] pdf = renderer.render(template, snapshotOf("PÉREZ, ANA"), code, URL);
            String raw = new String(pdf, StandardCharsets.ISO_8859_1);
            assertThat(raw).doesNotContain("/CreationDate", "/ModDate", "/Producer", "/Creator");
            try (PDDocument document = Loader.loadPDF(pdf)) {
                assertThat(document.getDocumentInformation().getCreationDate()).isNull();
                assertThat(document.getDocumentInformation().getModificationDate()).isNull();
                COSArray ids = (COSArray) document.getDocument().getTrailer().getDictionaryObject(COSName.ID);
                assertThat(ids).isNotNull();
                assertThat(ids.getString(0)).isEqualTo(ids.getString(1));
            }
        }
    }
}
