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

    private static final float PDF_MARGIN = 40;
    private static final float PDF_ROW_HEIGHT = 14;
    private static final float PDF_FONT_SIZE = 9;
    private static final float PDF_KEY_COLUMN_WIDTH = 160;
    private static final float PDF_DATA_COLUMN_WIDTH = 75;
    private static final float PDF_CELL_PADDING = 3;

    private byte[] generatePdf(Table table) {
        try (PDDocument document = new PDDocument()) {
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            float availableWidth = pageSize.getWidth() - 2 * PDF_MARGIN;
            int dataColumnsPerChunk = Math.max(1, (int) ((availableWidth - PDF_KEY_COLUMN_WIDTH) / PDF_DATA_COLUMN_WIDTH));
            int rowsPerPage = Math.max(1, (int) ((pageSize.getHeight() - 2 * PDF_MARGIN - 46) / PDF_ROW_HEIGHT));

            List<List<Integer>> columnChunks = splitColumns(table.headers().size(), dataColumnsPerChunk);
            for (int chunkIndex = 0; chunkIndex < columnChunks.size(); chunkIndex++) {
                String title = columnChunks.size() > 1
                        ? table.title() + " (parte " + (chunkIndex + 1) + " de " + columnChunks.size() + ")"
                        : table.title();
                List<Integer> columns = columnChunks.get(chunkIndex);
                int rowCount = table.rows().size();
                int pageCount = Math.max(1, (rowCount + rowsPerPage - 1) / rowsPerPage);
                for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                    int from = pageIndex * rowsPerPage;
                    int to = Math.min(rowCount, from + rowsPerPage);
                    drawPdfPage(document, pageSize, fontBold, fontRegular, title, table, columns, from, to);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de asistencia", e);
        }
    }

    private List<List<Integer>> splitColumns(int columnCount, int dataColumnsPerChunk) {
        List<List<Integer>> chunks = new ArrayList<>();
        for (int start = 1; start < columnCount || chunks.isEmpty(); start += dataColumnsPerChunk) {
            List<Integer> columns = new ArrayList<>();
            columns.add(0);
            for (int c = start; c < Math.min(columnCount, start + dataColumnsPerChunk); c++) {
                columns.add(c);
            }
            chunks.add(columns);
        }
        return chunks;
    }

    private void drawPdfPage(PDDocument document, PDRectangle pageSize, PDFont fontBold, PDFont fontRegular,
                             String title, Table table, List<Integer> columns, int fromRow, int toRow)
            throws IOException {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            float y = pageSize.getHeight() - PDF_MARGIN;
            drawPdfText(content, fontBold, 14, PDF_MARGIN, y, title);
            y -= 30;
            drawPdfRow(content, fontBold, columns, y, column -> table.headers().get(column));
            y -= PDF_ROW_HEIGHT + 2;
            for (int r = fromRow; r < toRow; r++) {
                List<String> row = table.rows().get(r);
                drawPdfRow(content, fontRegular, columns, y, row::get);
                y -= PDF_ROW_HEIGHT;
            }
        }
    }

    private void drawPdfRow(PDPageContentStream content, PDFont font, List<Integer> columns, float y,
                            java.util.function.IntFunction<String> valueOf) throws IOException {
        float x = PDF_MARGIN;
        for (int index = 0; index < columns.size(); index++) {
            int column = columns.get(index);
            float width = column == 0 ? PDF_KEY_COLUMN_WIDTH : PDF_DATA_COLUMN_WIDTH;
            drawPdfText(content, font, PDF_FONT_SIZE, x, y,
                    fitToWidth(font, valueOf.apply(column), width - PDF_CELL_PADDING));
            x += width;
        }
    }

    private void drawPdfText(PDPageContentStream content, PDFont font, float size, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private String fitToWidth(PDFont font, String text, float maxWidth) throws IOException {
        String value = text == null ? "" : text;
        if (font.getStringWidth(value) / 1000 * PDF_FONT_SIZE <= maxWidth) {
            return value;
        }
        String trimmed = value;
        while (!trimmed.isEmpty() && font.getStringWidth(trimmed + "...") / 1000 * PDF_FONT_SIZE > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
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
