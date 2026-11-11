package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ClosureWorkbookWriterTest {

    private final ClosureWorkbookWriter writer = new ClosureWorkbookWriter();

    private List<String> cellsOf(byte[] content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            List<String> cells = new ArrayList<>();
            workbook.getSheetAt(0).forEach(row -> row.forEach(cell -> cells.add(cell.getStringCellValue())));
            return cells;
        }
    }

    @Test
    void accentsAndEnyesSurviveTheRoundTripThroughTheSpreadsheet() throws IOException {
        byte[] content = writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true), ClosureViewerProfile.STAFF);

        assertThat(cellsOf(content)).contains("Muñoz Peña", "Núñez", "Íñigo", "Observación: ñandú", "Condición final")
                .anyMatch(cell -> cell.contains("Teorías Socioculturales"));
    }

    @Test
    void aDraftCarriesTheDraftLegendAndAClosedActaCarriesItsNumberInstead() throws IOException {
        List<String> draft = cellsOf(writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.TEACHER_CONFIRMED, true), ClosureViewerProfile.STAFF));
        List<String> closed = cellsOf(writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true), ClosureViewerProfile.STAFF));

        assertThat(draft).contains("BORRADOR, sin valor de acta");
        assertThat(closed).doesNotContain("BORRADOR, sin valor de acta").anyMatch(cell -> cell.contains("ACTA-2026-0042"));
    }

    @Test
    void theTeacherSpreadsheetOmitsPaymentsAndMissingDocumentsWhileStaffSeesThem() throws IOException {
        List<String> staff = cellsOf(writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true), ClosureViewerProfile.STAFF));
        List<String> teacher = cellsOf(writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, false), ClosureViewerProfile.TEACHER));

        assertThat(staff).contains("Pagos al día", "Documentos faltantes", "DNI");
        assertThat(teacher).contains("Legajo completo").doesNotContain("Pagos al día", "Documentos faltantes");
    }

    @Test
    void theEffectiveRetakeGradeAndTheOriginalOneAreInSeparateCellsAndWithdrawnRowsAreStruckThrough() throws IOException {
        byte[] content = writer.write(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true), ClosureViewerProfile.STAFF);

        assertThat(cellsOf(content)).contains("Parcial 1", "Parcial 1 (original)", "8.00", "4.00", "Baja");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row withdrawn = StreamSupport.stream(sheet.spliterator(), false)
                    .filter(row -> row.getCell(1) != null && "Ávila".equals(row.getCell(1).getStringCellValue()))
                    .findFirst().orElseThrow();
            Cell lastname = withdrawn.getCell(1);
            assertThat(workbook.getFontAt(lastname.getCellStyle().getFontIndex()).getStrikeout()).isTrue();
        }
    }
}
