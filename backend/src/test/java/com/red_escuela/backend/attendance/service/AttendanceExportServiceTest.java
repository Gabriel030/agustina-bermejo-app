package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceExportServiceTest {

    private static final long SECTION_ID = 5L;
    private static final long INSTRUCTOR_ID = 9L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 5, 4);
    private static final LocalDate SECOND_DAY = LocalDate.of(2026, 5, 11);
    private static final String XLSX = "xlsx";
    private static final String PDF_TYPE = "application/pdf";

    @Mock
    private AttendanceSectionAccess sectionAccess;
    @Mock
    private AttendanceHistoryBuilder historyBuilder;
    @Mock
    private SectionRosterBuilder rosterBuilder;

    private AttendanceExportService service() {
        return new AttendanceExportService(sectionAccess, historyBuilder, rosterBuilder);
    }

    private static AttendanceHistoryResponse history() {
        var ana = new AttendanceHistoryResponse.StudentHistoryRow(1L, "Ana Perez",
                Map.of(FIRST_DAY, new AttendanceHistoryResponse.HistoryCell(10L, "PRESENT")),
                Percentage.ratio(1, 3).orElseThrow());
        var beto = new AttendanceHistoryResponse.StudentHistoryRow(2L, "Beto Gomez", Map.of(), null);
        return new AttendanceHistoryResponse(List.of(FIRST_DAY, SECOND_DAY), List.of(ana, beto));
    }

    private static List<RosterEntryResponse> rawRoster() {
        return List.of(new RosterEntryResponse(2L, 2L, "Beto Gomez", null, 0, null, false, 0, null, null));
    }

    private static List<RosterEntryResponse> report() {
        return List.of(
                new RosterEntryResponse(1L, 1L, "Ana Perez", "30111222", 4, Percentage.ratio(2, 3).orElseThrow(), true, 6, null, null),
                new RosterEntryResponse(2L, 2L, "Beto Gomez", null, 0, null, false, 0, null, null));
    }

    private static List<List<String>> sheetRows(ExportedAttendanceFile file) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new java.util.ArrayList<>();
            for (var row : sheet) {
                List<String> values = new java.util.ArrayList<>();
                row.forEach(cell -> values.add(cell.getStringCellValue()));
                rows.add(values);
            }
            return rows;
        }
    }

    private static String sheetName(ExportedAttendanceFile file) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            return workbook.getSheetName(0);
        }
    }

    @Test
    void programHistoryExportsOneColumnPerClassDateAndTheRoundedPercentage() throws IOException {
        when(historyBuilder.buildForProgramSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "history", XLSX, null);

        assertThat(file.filename()).isEqualTo("history.xlsx");
        assertThat(file.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(sheetName(file)).isEqualTo("Historial de asistencia");
        assertThat(sheetRows(file)).containsExactly(
                List.of("Alumno", "2026-05-04", "2026-05-11", "% Asistencia"),
                List.of("Ana Perez", "PRESENT", "-", "33%"),
                List.of("Beto Gomez", "-", "-", "-"));
        verifyNoInteractions(rosterBuilder);
    }

    @Test
    void programExportChecksTheInstructorOwnsTheSection() throws IOException {
        when(historyBuilder.buildForProgramSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "history", XLSX, null);

        verify(sectionAccess).requireProgramSectionExists(SECTION_ID);
        verify(sectionAccess).requireOwnProgramSectionIfInstructorGiven(SECTION_ID, INSTRUCTOR_ID);
        assertThat(sheetRows(file)).hasSize(3);
    }

    @Test
    void courseExportChecksTheInstructorOwnsTheSection() throws IOException {
        when(historyBuilder.buildForCourseSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "history", XLSX, null);

        verify(sectionAccess).requireCourseSectionExists(SECTION_ID);
        verify(sectionAccess).requireOwnCourseSectionIfInstructorGiven(SECTION_ID, INSTRUCTOR_ID);
        assertThat(sheetRows(file)).hasSize(3);
    }

    @Test
    void courseHistoryExportsThroughTheCourseHistoryOnly() throws IOException {
        when(historyBuilder.buildForCourseSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportCourseSection(SECTION_ID, null, "history", "XLSX", null);

        assertThat(file.filename()).isEqualTo("history.xlsx");
        assertThat(sheetRows(file)).hasSize(3);
        verifyNoInteractions(rosterBuilder);
    }

    @Test
    void classScopeExportsTheStatusOfEachStudentOnTheRequestedDate() throws IOException {
        when(historyBuilder.buildForProgramSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "class", XLSX, FIRST_DAY);

        assertThat(sheetName(file)).isEqualTo("Asistencia · 2026-05-04");
        assertThat(sheetRows(file)).containsExactly(
                List.of("Alumno", "Estado"),
                List.of("Ana Perez", "PRESENT"),
                List.of("Beto Gomez", "-"));
    }

    @Test
    void classScopeWithoutDateDefaultsToToday() throws IOException {
        when(historyBuilder.buildForCourseSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "class", XLSX, null);

        assertThat(sheetName(file)).isEqualTo("Asistencia · " + ApplicationTime.today());
    }

    @Test
    void absenceReportExportsDniAbsencesAndDashesForMissingValues() throws IOException {
        when(rosterBuilder.buildForProgramSection(SECTION_ID)).thenReturn(rawRoster());
        when(rosterBuilder.sortByAbsences(rawRoster(), null)).thenReturn(report());

        ExportedAttendanceFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "absence-report", XLSX, null);

        assertThat(file.filename()).isEqualTo("absence-report.xlsx");
        assertThat(sheetRows(file)).containsExactly(
                List.of("Alumno", "DNI", "Ausencias", "% Asistencia"),
                List.of("Ana Perez", "30111222", "4", "67%"),
                List.of("Beto Gomez", "-", "0", "-"));
        verifyNoInteractions(historyBuilder);
    }

    @Test
    void courseAbsenceReportUsesTheCourseRoster() throws IOException {
        when(rosterBuilder.buildForCourseSection(SECTION_ID)).thenReturn(rawRoster());
        when(rosterBuilder.sortByAbsences(rawRoster(), null)).thenReturn(report());

        ExportedAttendanceFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "absence-report", XLSX, null);

        assertThat(sheetRows(file)).hasSize(3);
        verifyNoInteractions(historyBuilder);
    }

    @Test
    void anyFormatOtherThanXlsxProducesAPdfDocument() {
        when(historyBuilder.buildForProgramSection(SECTION_ID)).thenReturn(history());

        ExportedAttendanceFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "history", "pdf", null);

        assertThat(file.filename()).isEqualTo("history.pdf");
        assertThat(file.contentType()).isEqualTo(PDF_TYPE);
        assertThat(new String(file.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void absenceReportAsPdfIsAlsoAValidPdf() {
        when(rosterBuilder.buildForCourseSection(SECTION_ID)).thenReturn(rawRoster());
        when(rosterBuilder.sortByAbsences(rawRoster(), null)).thenReturn(report());

        ExportedAttendanceFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "absence-report", "anything", null);

        assertThat(file.contentType()).isEqualTo(PDF_TYPE);
        assertThat(file.content().length).isGreaterThan(100);
    }

    @Test
    void unknownScopeIsRejectedBeforeBuildingAnything() {
        assertThatThrownBy(() -> service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "weekly", XLSX, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekly");

        verifyNoInteractions(sectionAccess, historyBuilder, rosterBuilder);
    }

    private static final int BIG_STUDENTS = 300;
    private static final int BIG_DATES = 40;

    private static AttendanceHistoryResponse bigHistory() {
        List<LocalDate> dates = new java.util.ArrayList<>();
        for (int d = 0; d < BIG_DATES; d++) {
            dates.add(FIRST_DAY.plusDays(d));
        }
        List<AttendanceHistoryResponse.StudentHistoryRow> rows = new java.util.ArrayList<>();
        for (int s = 1; s <= BIG_STUDENTS; s++) {
            Map<LocalDate, AttendanceHistoryResponse.HistoryCell> cells = new java.util.HashMap<>();
            dates.forEach(date -> cells.put(date, new AttendanceHistoryResponse.HistoryCell(1L, "PRESENT")));
            rows.add(new AttendanceHistoryResponse.StudentHistoryRow(
                    (long) s, "Alumno%03d".formatted(s), cells, Percentage.ratio(1, 2).orElseThrow()));
        }
        return new AttendanceHistoryResponse(dates, rows);
    }

    private static final class BoundsStripper extends PDFTextStripper {
        float maxX;
        float maxY;

        BoundsStripper() throws IOException {
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            for (TextPosition position : positions) {
                maxX = Math.max(maxX, position.getX() + position.getWidth());
                maxY = Math.max(maxY, position.getY());
            }
            super.writeString(text, positions);
        }
    }

    @Test
    void largePdfHistorySplitsIntoPagesAndKeepsEveryRowAndDate() throws IOException {
        when(historyBuilder.buildForProgramSection(SECTION_ID)).thenReturn(bigHistory());

        ExportedAttendanceFile file = service().exportProgramSection(SECTION_ID, null, "history", "pdf", null);

        try (PDDocument document = Loader.loadPDF(file.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(10);
            BoundsStripper stripper = new BoundsStripper();
            String text = stripper.getText(document);
            float pageWidth = document.getPage(0).getMediaBox().getWidth();
            float pageHeight = document.getPage(0).getMediaBox().getHeight();
            assertThat(text).contains("Alumno300", "Alumno001", FIRST_DAY.plusDays(BIG_DATES - 1).toString(),
                    "% Asistencia", "parte 1 de", "parte 6 de 6");
            assertThat(stripper.maxX).isLessThanOrEqualTo(pageWidth - 20);
            assertThat(stripper.maxY).isLessThanOrEqualTo(pageHeight - 20);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper pageStripper = new PDFTextStripper();
                pageStripper.setStartPage(page);
                pageStripper.setEndPage(page);
                assertThat(pageStripper.getText(document)).contains("Alumno ");
            }
        }
    }
}
