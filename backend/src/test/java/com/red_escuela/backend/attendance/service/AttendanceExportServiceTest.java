package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.service.AttendanceExportService.ExportedFile;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAbsenceReportCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAbsenceReportUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAttendanceHistoryCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAttendanceHistoryUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAbsenceReportCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAbsenceReportUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAttendanceHistoryCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAttendanceHistoryUseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.ApplicationTime;
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
import static org.mockito.Mockito.never;
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
    private GetProgramSectionAttendanceHistoryUseCase programHistory;
    @Mock
    private GetCourseSectionAttendanceHistoryUseCase courseHistory;
    @Mock
    private GetProgramSectionAbsenceReportUseCase programReport;
    @Mock
    private GetCourseSectionAbsenceReportUseCase courseReport;

    private AttendanceExportService service() {
        return new AttendanceExportService(programHistory, courseHistory, programReport, courseReport);
    }

    private static AttendanceHistoryResponse history() {
        var ana = new AttendanceHistoryResponse.StudentHistoryRow(1L, "Ana Perez",
                Map.of(FIRST_DAY, new AttendanceHistoryResponse.HistoryCell(10L, "PRESENT")),
                Percentage.ratio(1, 3).orElseThrow());
        var beto = new AttendanceHistoryResponse.StudentHistoryRow(2L, "Beto Gomez", Map.of(), null);
        return new AttendanceHistoryResponse(List.of(FIRST_DAY, SECOND_DAY), List.of(ana, beto));
    }

    private static List<RosterEntryResponse> report() {
        return List.of(
                new RosterEntryResponse(1L, 1L, "Ana Perez", "30111222", 4, Percentage.ratio(2, 3).orElseThrow(), true),
                new RosterEntryResponse(2L, 2L, "Beto Gomez", null, 0, null, false));
    }

    private static List<List<String>> sheetRows(ExportedFile file) throws IOException {
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

    private static String sheetName(ExportedFile file) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            return workbook.getSheetName(0);
        }
    }

    @Test
    void programHistoryExportsOneColumnPerClassDateAndTheRoundedPercentage() throws IOException {
        when(programHistory.get(new GetProgramSectionAttendanceHistoryCommand(SECTION_ID, INSTRUCTOR_ID))).thenReturn(history());

        ExportedFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "history", XLSX, null);

        assertThat(file.filename()).isEqualTo("history.xlsx");
        assertThat(file.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(sheetName(file)).isEqualTo("Historial de asistencia");
        assertThat(sheetRows(file)).containsExactly(
                List.of("Alumno", "2026-05-04", "2026-05-11", "% Asistencia"),
                List.of("Ana Perez", "PRESENT", "-", "33%"),
                List.of("Beto Gomez", "-", "-", "-"));
        verifyNoInteractions(programReport);
    }

    @Test
    void courseHistoryExportsThroughTheCourseUseCaseOnly() throws IOException {
        when(courseHistory.get(new GetCourseSectionAttendanceHistoryCommand(SECTION_ID, null))).thenReturn(history());

        ExportedFile file = service().exportCourseSection(SECTION_ID, null, "history", "XLSX", null);

        assertThat(file.filename()).isEqualTo("history.xlsx");
        assertThat(sheetRows(file)).hasSize(3);
        verifyNoInteractions(programHistory);
    }

    @Test
    void classScopeExportsTheStatusOfEachStudentOnTheRequestedDate() throws IOException {
        when(programHistory.get(new GetProgramSectionAttendanceHistoryCommand(SECTION_ID, INSTRUCTOR_ID))).thenReturn(history());

        ExportedFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "class", XLSX, FIRST_DAY);

        assertThat(sheetName(file)).isEqualTo("Asistencia · 2026-05-04");
        assertThat(sheetRows(file)).containsExactly(
                List.of("Alumno", "Estado"),
                List.of("Ana Perez", "PRESENT"),
                List.of("Beto Gomez", "-"));
    }

    @Test
    void classScopeWithoutDateDefaultsToToday() throws IOException {
        when(courseHistory.get(new GetCourseSectionAttendanceHistoryCommand(SECTION_ID, INSTRUCTOR_ID))).thenReturn(history());

        ExportedFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "class", XLSX, null);

        assertThat(sheetName(file)).isEqualTo("Asistencia · " + ApplicationTime.today());
    }

    @Test
    void absenceReportExportsDniAbsencesAndDashesForMissingValues() throws IOException {
        when(programReport.get(new GetProgramSectionAbsenceReportCommand(SECTION_ID, INSTRUCTOR_ID, null))).thenReturn(report());

        ExportedFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "absence-report", XLSX, null);

        assertThat(file.filename()).isEqualTo("absence-report.xlsx");
        assertThat(sheetRows(file)).containsExactly(
                List.of("Alumno", "DNI", "Ausencias", "% Asistencia"),
                List.of("Ana Perez", "30111222", "4", "67%"),
                List.of("Beto Gomez", "-", "0", "-"));
        verifyNoInteractions(programHistory);
    }

    @Test
    void courseAbsenceReportUsesTheCourseReportUseCase() throws IOException {
        when(courseReport.get(new GetCourseSectionAbsenceReportCommand(SECTION_ID, INSTRUCTOR_ID, null))).thenReturn(report());

        ExportedFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "absence-report", XLSX, null);

        assertThat(sheetRows(file)).hasSize(3);
        verifyNoInteractions(courseHistory);
    }

    @Test
    void anyFormatOtherThanXlsxProducesAPdfDocument() {
        when(programHistory.get(new GetProgramSectionAttendanceHistoryCommand(SECTION_ID, INSTRUCTOR_ID))).thenReturn(history());

        ExportedFile file = service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "history", "pdf", null);

        assertThat(file.filename()).isEqualTo("history.pdf");
        assertThat(file.contentType()).isEqualTo(PDF_TYPE);
        assertThat(new String(file.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void absenceReportAsPdfIsAlsoAValidPdf() {
        when(courseReport.get(new GetCourseSectionAbsenceReportCommand(SECTION_ID, INSTRUCTOR_ID, null))).thenReturn(report());

        ExportedFile file = service().exportCourseSection(SECTION_ID, INSTRUCTOR_ID, "absence-report", "anything", null);

        assertThat(file.contentType()).isEqualTo(PDF_TYPE);
        assertThat(file.content().length).isGreaterThan(100);
    }

    @Test
    void unknownScopeIsRejectedWithoutReadingAnyData() {
        assertThatThrownBy(() -> service().exportProgramSection(SECTION_ID, INSTRUCTOR_ID, "weekly", XLSX, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekly");

        verifyNoInteractions(programHistory, courseHistory, programReport, courseReport);
        verify(programHistory, never()).get(org.mockito.ArgumentMatchers.any());
    }
}
