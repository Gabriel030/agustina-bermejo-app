package com.red_escuela.backend.graduation.certificate.approvedsubjects.render;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectLine;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsRecipient;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.QrMatrixFactory;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedSubjectsPdfRendererTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";

    private final ApprovedSubjectsPdfRenderer renderer = new ApprovedSubjectsPdfRenderer(new QrMatrixFactory());
    private final ApprovedSubjectsSnapshotFactory factory = new ApprovedSubjectsSnapshotFactory();
    private final CertificateCode code = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");

    private ApprovedSubjectsSnapshot snapshot(int subjects, ApprovedSubjectsRecipient recipient) {
        List<ApprovedSubjectLine> lines = new ArrayList<>();
        for (int i = 1; i <= subjects; i++) {
            lines.add(new ApprovedSubjectLine("Materia número " + i, 1 + i / 10, new BigDecimal("8.00"), LocalDate.of(2026, 7, 1)));
        }
        return factory.create(new ApprovedSubjectsSnapshotFactory.Input("PÉREZ, ANA", "DNI", "30123456", "Tecnicatura en Enfermería",
                "Instituto de prueba", lines, Math.max(subjects, 1), recipient, LocalDate.of(2026, 11, 5),
                List.of(new SignatorySnapshot("Dirección", "Dirección académica"))));
    }

    private int pages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void fewSubjectsFitInOnePage() throws IOException {
        byte[] pdf = renderer.render(snapshot(5, null), code, URL);

        assertThat(pages(pdf)).isEqualTo(1);
    }

    @Test
    void manySubjectsSpanSeveralPagesAndEverySubjectAppears() throws IOException {
        byte[] pdf = renderer.render(snapshot(80, null), code, URL);

        String text = text(pdf);
        assertThat(pages(pdf)).isGreaterThan(1);
        assertThat(text).contains("Materia número 1").contains("Materia número 80");
        assertThat(text).contains("Página 1 de " + pages(pdf)).contains("(continuación)");
    }

    @Test
    void lastPageAlwaysHasRoomForTheFooterBlock() throws IOException {
        for (int subjects = 0; subjects <= 60; subjects++) {
            byte[] pdf = renderer.render(snapshot(subjects, null), code, URL);

            assertThat(text(pdf)).as("subjects=" + subjects).contains("Promedio general").contains(code.formatted());
        }
    }

    @Test
    void printsCodeVerificationUrlValidityAndTotals() throws IOException {
        String text = text(renderer.render(snapshot(4, null), code, URL));

        assertThat(text).contains(code.formatted()).contains(URL).contains("Válida hasta el 05/12/2026")
                .contains("Materias aprobadas: 4 de 4").contains("Promedio general: 8,00");
    }

    @Test
    void printsRecipientLiterally() throws IOException {
        String text = text(renderer.render(snapshot(3, new ApprovedSubjectsRecipient(RecipientType.HEALTH_INSURANCE, "OSDE <Plan 210> & Cía")), code, URL));

        assertThat(text).contains("Obra social").contains("OSDE <Plan 210> & Cía");
    }

    @Test
    void omitsRecipientLineWhenThereIsNone() throws IOException {
        assertThat(text(renderer.render(snapshot(3, null), code, URL))).doesNotContain("presentada ante");
    }

    @Test
    void sameInputProducesIdenticalBytes() {
        ApprovedSubjectsSnapshot snapshot = snapshot(80, new ApprovedSubjectsRecipient(RecipientType.OTHER, "Municipalidad"));

        assertThat(renderer.render(snapshot, code, URL)).isEqualTo(renderer.render(snapshot, code, URL));
    }

    @Test
    void differentCodeChangesTheBytes() {
        ApprovedSubjectsSnapshot snapshot = snapshot(3, null);

        assertThat(renderer.render(snapshot, code, URL))
                .isNotEqualTo(renderer.render(snapshot, new CertificateCode("0QK3M9XH2VBN4T8R5W6YZ0A1B2"), URL));
    }
}
