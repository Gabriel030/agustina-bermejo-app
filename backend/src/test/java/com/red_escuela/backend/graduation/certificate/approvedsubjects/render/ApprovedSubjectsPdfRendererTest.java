package com.red_escuela.backend.graduation.certificate.approvedsubjects.render;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectLine;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsRecipient;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.QrMatrixFactory;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedSubjectsPdfRendererTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";

    private final ApprovedSubjectsPdfRenderer renderer = new ApprovedSubjectsPdfRenderer(new QrMatrixFactory(), new CertificateVerificationUrl("https://app.example/"));
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
        byte[] pdf = renderer.render(snapshot(5, null), code);

        assertThat(pages(pdf)).isEqualTo(1);
    }

    @Test
    void manySubjectsSpanSeveralPagesAndEverySubjectAppears() throws IOException {
        byte[] pdf = renderer.render(snapshot(80, null), code);

        String text = text(pdf);
        assertThat(pages(pdf)).isGreaterThan(1);
        assertThat(text).contains("Materia número 1").contains("Materia número 80");
        assertThat(text).contains("Página 1 de " + pages(pdf)).contains("(continuación)");
    }

    @Test
    void lastPageAlwaysHasRoomForTheFooterBlock() throws IOException {
        for (int subjects = 0; subjects <= 60; subjects++) {
            byte[] pdf = renderer.render(snapshot(subjects, null), code);

            assertThat(text(pdf)).as("subjects=" + subjects).contains("Promedio general").contains(code.formatted());
        }
    }

    @Test
    void printsCodeVerificationUrlValidityAndTotals() throws IOException {
        String text = text(renderer.render(snapshot(4, null), code));

        assertThat(text).contains(code.formatted()).contains(URL).contains("Válida hasta el 05/12/2026")
                .contains("Materias aprobadas: 4 de 4").contains("Promedio general: 8,00");
    }

    @Test
    void printsRecipientLiterally() throws IOException {
        String text = text(renderer.render(snapshot(3, new ApprovedSubjectsRecipient(RecipientType.HEALTH_INSURANCE, "OSDE <Plan 210> & Cía")), code));

        assertThat(text).contains("Obra social").contains("OSDE <Plan 210> & Cía");
    }

    @Test
    void omitsRecipientLineWhenThereIsNone() throws IOException {
        assertThat(text(renderer.render(snapshot(3, null), code))).doesNotContain("presentada ante");
    }

    @Test
    void sameInputProducesIdenticalBytes() {
        ApprovedSubjectsSnapshot snapshot = snapshot(80, new ApprovedSubjectsRecipient(RecipientType.OTHER, "Municipalidad"));

        assertThat(renderer.render(snapshot, code)).isEqualTo(renderer.render(snapshot, code));
    }

    @Test
    void differentCodeChangesTheBytes() {
        ApprovedSubjectsSnapshot snapshot = snapshot(3, null);

        assertThat(renderer.render(snapshot, code))
                .isNotEqualTo(renderer.render(snapshot, new CertificateCode("0QK3M9XH2VBN4T8R5W6YZ0A1B2")));
    }

    private ApprovedSubjectsSnapshot snapshotWithNames(List<String> names, ApprovedSubjectsRecipient recipient) {
        List<ApprovedSubjectLine> lines = new ArrayList<>();
        for (String name : names) {
            lines.add(new ApprovedSubjectLine(name, 1, new BigDecimal("8.00"), LocalDate.of(2026, 7, 1)));
        }
        return factory.create(new ApprovedSubjectsSnapshotFactory.Input("PÉREZ, ANA", "DNI", "30123456", "Tecnicatura en Enfermería",
                "Instituto de prueba", lines, Math.max(names.size(), 1), recipient, LocalDate.of(2026, 11, 5),
                List.of(new SignatorySnapshot("Dirección", "Dirección académica"))));
    }

    private Map<Float, String> linesFromBottomOnLastPage(byte[] pdf) throws IOException {
        Map<Float, StringBuilder> collected = new TreeMap<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    float fromBottom = Math.round(getCurrentPage().getMediaBox().getHeight() - positions.get(0).getYDirAdj());
                    collected.computeIfAbsent(fromBottom, key -> new StringBuilder()).append(text);
                }
            };
            stripper.setStartPage(document.getNumberOfPages());
            stripper.getText(document);
        }
        Map<Float, String> lines = new TreeMap<>();
        collected.forEach((key, value) -> lines.put(key, value.toString()));
        return lines;
    }

    @Test
    void verificationUrlIsBuiltFromTheCode() throws IOException {
        assertThat(text(renderer.render(snapshot(3, null), code))).contains("https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2");
    }

    @Test
    void overlongSubjectNameIsTrimmedWithEllipsis() throws IOException {
        String longName = "Epidemiología y salud pública comunitaria aplicada a poblaciones vulnerables en contextos rurales y urbanos";

        String text = text(renderer.render(snapshotWithNames(List.of(longName), null), code));

        assertThat(text).contains("...").doesNotContain(longName);
    }

    @Test
    void unencodableCharactersAreReplacedWithQuestionMark() throws IOException {
        String text = text(renderer.render(snapshotWithNames(List.of("Materia \u6F22 especial"), null), code));

        assertThat(text).contains("Materia ? especial");
    }

    @Test
    void recipientWithoutSpacesIsWrappedInsideThePage() throws IOException {
        String recipient = "W".repeat(80);

        byte[] pdf = renderer.render(snapshot(3, new ApprovedSubjectsRecipient(RecipientType.OTHER, recipient)), code);

        assertThat(text(pdf).replaceAll("\\s", "")).contains(recipient);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        assertThat(position.getXDirAdj() + position.getWidthDirAdj()).isLessThanOrEqualTo(595.3f - 40f);
                    }
                }
            };
            stripper.getText(document);
        }
    }

    @Test
    void footerLinesNeverReachTheQrBlockOnTheLastPage() throws IOException {
        float qrTop = 60 + 20 + 96;
        ApprovedSubjectsRecipient recipient = new ApprovedSubjectsRecipient(RecipientType.OTHER, "W".repeat(80));
        for (int subjects = 0; subjects <= 60; subjects++) {
            Map<Float, String> lines = linesFromBottomOnLastPage(renderer.render(snapshot(subjects, recipient), code));

            lines.forEach((fromBottom, line) -> {
                if (line.startsWith("Emitida el") || line.startsWith("Promedio") || line.startsWith("Se extiende")) {
                    assertThat(fromBottom).as(line).isGreaterThan(qrTop);
                }
            });
            assertThat(lines.values()).as("subjects=" + subjects).anyMatch(line -> line.startsWith("Emitida el"));
        }
    }
}
