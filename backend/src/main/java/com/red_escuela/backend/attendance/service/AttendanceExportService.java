package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AttendanceExportService {

    public record ExportedFile(byte[] content, String filename, String contentType) {
    }

    private final GetProgramSectionAttendanceHistoryUseCase getProgramSectionAttendanceHistoryUseCase;
    private final GetCourseSectionAttendanceHistoryUseCase getCourseSectionAttendanceHistoryUseCase;
    private final GetProgramSectionAbsenceReportUseCase getProgramSectionAbsenceReportUseCase;
    private final GetCourseSectionAbsenceReportUseCase getCourseSectionAbsenceReportUseCase;

    public AttendanceExportService(GetProgramSectionAttendanceHistoryUseCase getProgramSectionAttendanceHistoryUseCase,
                                    GetCourseSectionAttendanceHistoryUseCase getCourseSectionAttendanceHistoryUseCase,
                                    GetProgramSectionAbsenceReportUseCase getProgramSectionAbsenceReportUseCase,
                                    GetCourseSectionAbsenceReportUseCase getCourseSectionAbsenceReportUseCase) {
        this.getProgramSectionAttendanceHistoryUseCase = getProgramSectionAttendanceHistoryUseCase;
        this.getCourseSectionAttendanceHistoryUseCase = getCourseSectionAttendanceHistoryUseCase;
        this.getProgramSectionAbsenceReportUseCase = getProgramSectionAbsenceReportUseCase;
        this.getCourseSectionAbsenceReportUseCase = getCourseSectionAbsenceReportUseCase;
    }

    public ExportedFile exportProgramSection(
            Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
        Table table = buildTable(scope, classDate,
                () -> getProgramSectionAttendanceHistoryUseCase.get(
                        new GetProgramSectionAttendanceHistoryCommand(sectionId, instructorIdIfDocente)),
                () -> getProgramSectionAbsenceReportUseCase.get(
                        new GetProgramSectionAbsenceReportCommand(sectionId, instructorIdIfDocente, null)));
        return generate(table, scope, format);
    }

    public ExportedFile exportCourseSection(
            Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
        Table table = buildTable(scope, classDate,
                () -> getCourseSectionAttendanceHistoryUseCase.get(
                        new GetCourseSectionAttendanceHistoryCommand(sectionId, instructorIdIfDocente)),
                () -> getCourseSectionAbsenceReportUseCase.get(
                        new GetCourseSectionAbsenceReportCommand(sectionId, instructorIdIfDocente, null)));
        return generate(table, scope, format);
    }

    private record Table(String title, List<String> headers, List<List<String>> rows) {
    }

    private Table buildTable(
            String scope, LocalDate classDate,
            java.util.function.Supplier<AttendanceHistoryResponse> history,
            java.util.function.Supplier<List<RosterEntryResponse>> absenceReport) {
        return switch (scope) {
            case "class" -> classTable(history.get(), classDate != null ? classDate : ApplicationTime.today());
            case "history" -> historyTable(history.get());
            case "absence-report" -> reportTable(absenceReport.get());
            default -> throw new IllegalArgumentException("scope de exportación desconocido: " + scope);
        };
    }

    private Table classTable(AttendanceHistoryResponse history, LocalDate classDate) {
        List<String> headers = List.of("Alumno", "Estado");
        List<List<String>> rows = history.rows().stream()
                .map(row -> List.of(row.studentName(), statusOf(row, classDate)))
                .toList();
        return new Table("Asistencia · " + classDate, headers, rows);
    }

    private Table historyTable(AttendanceHistoryResponse history) {
        List<String> headers = new ArrayList<>();
        headers.add("Alumno");
        history.classDates().forEach(date -> headers.add(date.toString()));
        headers.add("% Asistencia");

        List<List<String>> rows = history.rows().stream().map(row -> {
            List<String> values = new ArrayList<>();
            values.add(row.studentName());
            history.classDates().forEach(date -> values.add(statusOf(row, date)));
            values.add(formatPercent(row.attendancePercent()));
            return values;
        }).toList();
        return new Table("Historial de asistencia", headers, rows);
    }

    private String statusOf(AttendanceHistoryResponse.StudentHistoryRow row, LocalDate date) {
        AttendanceHistoryResponse.HistoryCell cell = row.cellsByDate().get(date);
        return cell == null ? "-" : cell.status();
    }

    private String formatPercent(Percentage percentage) {
        return percentage == null ? "-" : percentage.points().setScale(0, RoundingMode.HALF_UP) + "%";
    }

    private Table reportTable(List<RosterEntryResponse> report) {
        List<String> headers = List.of("Alumno", "DNI", "Ausencias", "% Asistencia");
        List<List<String>> rows = report.stream()
                .map(entry -> List.of(
                        entry.studentName(), entry.dni() == null ? "-" : entry.dni(),
                        String.valueOf(entry.absenceCount()),
                        formatPercent(entry.attendancePercent())))
                .toList();
        return new Table("Reporte de ausencias", headers, rows);
    }

    private ExportedFile generate(Table table, String scope, String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            return new ExportedFile(generateXlsx(table), scope + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        return new ExportedFile(generatePdf(table), scope + ".pdf", "application/pdf");
    }

    private byte[] generatePdf(Table table) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 40;
                float y = page.getMediaBox().getHeight() - margin;

                content.beginText();
                content.setFont(fontBold, 14);
                content.newLineAtOffset(margin, y);
                content.showText(table.title());
                content.endText();
                y -= 30;

                content.beginText();
                content.setFont(fontBold, 9);
                content.newLineAtOffset(margin, y);
                content.showText(String.join("   |   ", table.headers()));
                content.endText();
                y -= 16;

                content.beginText();
                content.setFont(fontRegular, 9);
                content.newLineAtOffset(margin, y);
                for (List<String> row : table.rows()) {
                    content.showText(String.join("   |   ", row));
                    content.newLineAtOffset(0, -14);
                }
                content.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de asistencia", e);
        }
    }

    private byte[] generateXlsx(Table table) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(table.title());
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < table.headers().size(); i++) {
                headerRow.createCell(i).setCellValue(table.headers().get(i));
            }
            for (int r = 0; r < table.rows().size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = table.rows().get(r);
                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(values.get(c));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el XLSX de asistencia", e);
        }
    }
}
