package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClosureActaPdfWriterTest {

    private final ClosureActaPdfWriter writer = new ClosureActaPdfWriter();

    private String textOf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void accentsAndEnyesAreExtractedFromTheEmbeddedFontWithoutReplacementCharacters() throws IOException {
        String text = textOf(writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true)));

        assertThat(text).contains("Muñoz Peña", "Núñez", "Teorías Socioculturales", "COMISIÓN").doesNotContain("?", "�");
    }

    @Test
    void theActaCarriesNumberVersionBookAndFolioButNoPaymentsNorDocuments() throws IOException {
        String text = textOf(writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true)));

        assertThat(text).contains("ACTA-2026-0042", "versión 1", "Libro 3", "Folio 12", "Total de promociones: 1", "Firma del docente")
                .doesNotContain("Pagos", "Legajo", "Documentos");
    }

    @Test
    void aLongStudentListSpillsOverToAnotherPageKeepingTheHeaderAndTheSignatureBlock() throws IOException {
        var sheet = ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true);
        var many = new java.util.ArrayList<>(sheet.students());
        for (int index = 0; index < 60; index++) {
            many.add(sheet.students().get(0));
        }
        var long_sheet = new com.red_escuela.backend.evaluation.dto.ClosureSheetResponse(sheet.closureId(), sheet.sectionId(),
                sheet.sectionName(), sheet.subjectName(), sheet.programName(), sheet.termLabel(), sheet.termEndDate(), sheet.instructors(),
                sheet.status(), sheet.availability(), sheet.scheme(), sheet.columns(), many, sheet.summary(), sheet.reportNumber(),
                sheet.closedAt(), sheet.reportVersion(), sheet.book(), sheet.folio());

        byte[] content = writer.write(long_sheet);

        try (PDDocument document = Loader.loadPDF(content)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(textOf(content)).contains("Firma de Secretaría Académica");
    }
}
