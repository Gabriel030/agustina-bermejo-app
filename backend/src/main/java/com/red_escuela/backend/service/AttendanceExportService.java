package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.dto.RosterEntryResponse;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Exportación on-demand de asistencia (clase puntual / historial / reporte de ausencias) a
 * PDF/XLS — mismo criterio que CertificateService: nunca se persiste el archivo generado, solo
 * los datos que ya existen.
 */
@Service
public class AttendanceExportService {

    public record ExportedFile(byte[] content, String filename, String contentType) {
    }

    private final AttendanceService attendanceService;

    public AttendanceExportService(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public ExportedFile exportProgramSection(
            Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
        Tabla tabla = buildTable(scope, classDate,
                () -> attendanceService.getHistoryProgramSection(sectionId, instructorIdIfDocente),
                () -> attendanceService.absenceReportProgramSection(sectionId, instructorIdIfDocente, null));
        return generate(tabla, scope, format);
    }

    public ExportedFile exportCourseSection(
            Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
        Tabla tabla = buildTable(scope, classDate,
                () -> attendanceService.getHistoryCourseSection(sectionId, instructorIdIfDocente),
                () -> attendanceService.absenceReportCourseSection(sectionId, instructorIdIfDocente, null));
        return generate(tabla, scope, format);
    }

    private record Tabla(String titulo, List<String> encabezados, List<List<String>> filas) {
    }

    private Tabla buildTable(
            String scope, LocalDate classDate,
            java.util.function.Supplier<AttendanceHistoryResponse> historial,
            java.util.function.Supplier<List<RosterEntryResponse>> reporteDeAusencias) {
        return switch (scope) {
            case "class" -> classTable(historial.get(), classDate != null ? classDate : LocalDate.now());
            case "history" -> historyTable(historial.get());
            case "absence-report" -> reportTable(reporteDeAusencias.get());
            default -> throw new IllegalArgumentException("scope de exportación desconocido: " + scope);
        };
    }

    private Tabla classTable(AttendanceHistoryResponse historial, LocalDate classDate) {
        List<String> encabezados = List.of("Alumno", "Estado");
        List<List<String>> filas = historial.rows().stream()
                .map(fila -> List.of(fila.studentName(), statusOf(fila, classDate)))
                .toList();
        return new Tabla("Asistencia · " + classDate, encabezados, filas);
    }

    private Tabla historyTable(AttendanceHistoryResponse historial) {
        List<String> encabezados = new ArrayList<>();
        encabezados.add("Alumno");
        historial.classDates().forEach(fecha -> encabezados.add(fecha.toString()));
        encabezados.add("% Asistencia");

        List<List<String>> filas = historial.rows().stream().map(fila -> {
            List<String> valores = new ArrayList<>();
            valores.add(fila.studentName());
            historial.classDates().forEach(fecha -> valores.add(statusOf(fila, fecha)));
            valores.add(fila.attendancePercentage() == null ? "-" : Math.round(fila.attendancePercentage() * 100) + "%");
            return valores;
        }).toList();
        return new Tabla("Historial de asistencia", encabezados, filas);
    }

    private String statusOf(AttendanceHistoryResponse.StudentHistoryRow fila, LocalDate fecha) {
        AttendanceHistoryResponse.HistoryCell celda = fila.cellsByDate().get(fecha);
        return celda == null ? "-" : celda.status();
    }

    private Tabla reportTable(List<RosterEntryResponse> reporte) {
        List<String> encabezados = List.of("Alumno", "DNI", "Ausencias", "% Asistencia");
        List<List<String>> filas = reporte.stream()
                .map(entrada -> List.of(
                        entrada.studentName(), entrada.dni() == null ? "-" : entrada.dni(),
                        String.valueOf(entrada.absenceCount()),
                        entrada.attendancePercentage() == null ? "-" : Math.round(entrada.attendancePercentage() * 100) + "%"))
                .toList();
        return new Tabla("Reporte de ausencias", encabezados, filas);
    }

    private ExportedFile generate(Tabla tabla, String scope, String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            return new ExportedFile(generateXlsx(tabla), scope + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        return new ExportedFile(generatePdf(tabla), scope + ".pdf", "application/pdf");
    }

    private byte[] generatePdf(Tabla tabla) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margen = 40;
                float y = page.getMediaBox().getHeight() - margen;

                content.beginText();
                content.setFont(fontBold, 14);
                content.newLineAtOffset(margen, y);
                content.showText(tabla.titulo());
                content.endText();
                y -= 30;

                content.beginText();
                content.setFont(fontBold, 9);
                content.newLineAtOffset(margen, y);
                content.showText(String.join("   |   ", tabla.encabezados()));
                content.endText();
                y -= 16;

                content.beginText();
                content.setFont(fontRegular, 9);
                content.newLineAtOffset(margen, y);
                for (List<String> fila : tabla.filas()) {
                    content.showText(String.join("   |   ", fila));
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

    private byte[] generateXlsx(Tabla tabla) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(tabla.titulo());
            Row encabezado = sheet.createRow(0);
            for (int i = 0; i < tabla.encabezados().size(); i++) {
                encabezado.createCell(i).setCellValue(tabla.encabezados().get(i));
            }
            for (int f = 0; f < tabla.filas().size(); f++) {
                Row row = sheet.createRow(f + 1);
                List<String> fila = tabla.filas().get(f);
                for (int c = 0; c < fila.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(fila.get(c));
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
