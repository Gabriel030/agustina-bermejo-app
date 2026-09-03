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

    public ExportedFile exportarProgramSection(
            Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
        Tabla tabla = construirTabla(scope, classDate,
                () -> attendanceService.obtenerHistorialProgramSection(sectionId, instructorIdIfDocente),
                () -> attendanceService.reporteDeAusenciasProgramSection(sectionId, instructorIdIfDocente, null));
        return generar(tabla, scope, format);
    }

    public ExportedFile exportarCourseSection(
            Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
        Tabla tabla = construirTabla(scope, classDate,
                () -> attendanceService.obtenerHistorialCourseSection(sectionId, instructorIdIfDocente),
                () -> attendanceService.reporteDeAusenciasCourseSection(sectionId, instructorIdIfDocente, null));
        return generar(tabla, scope, format);
    }

    private record Tabla(String titulo, List<String> encabezados, List<List<String>> filas) {
    }

    private Tabla construirTabla(
            String scope, LocalDate classDate,
            java.util.function.Supplier<AttendanceHistoryResponse> historial,
            java.util.function.Supplier<List<RosterEntryResponse>> reporteDeAusencias) {
        return switch (scope) {
            case "class" -> tablaDeClase(historial.get(), classDate != null ? classDate : LocalDate.now());
            case "history" -> tablaDeHistorial(historial.get());
            case "absence-report" -> tablaDeReporte(reporteDeAusencias.get());
            default -> throw new IllegalArgumentException("scope de exportación desconocido: " + scope);
        };
    }

    private Tabla tablaDeClase(AttendanceHistoryResponse historial, LocalDate classDate) {
        List<String> encabezados = List.of("Alumno", "Estado");
        List<List<String>> filas = historial.rows().stream()
                .map(fila -> List.of(fila.studentName(), estadoDe(fila, classDate)))
                .toList();
        return new Tabla("Asistencia · " + classDate, encabezados, filas);
    }

    private Tabla tablaDeHistorial(AttendanceHistoryResponse historial) {
        List<String> encabezados = new ArrayList<>();
        encabezados.add("Alumno");
        historial.classDates().forEach(fecha -> encabezados.add(fecha.toString()));
        encabezados.add("% Asistencia");

        List<List<String>> filas = historial.rows().stream().map(fila -> {
            List<String> valores = new ArrayList<>();
            valores.add(fila.studentName());
            historial.classDates().forEach(fecha -> valores.add(estadoDe(fila, fecha)));
            valores.add(fila.attendancePercentage() == null ? "-" : Math.round(fila.attendancePercentage() * 100) + "%");
            return valores;
        }).toList();
        return new Tabla("Historial de asistencia", encabezados, filas);
    }

    private String estadoDe(AttendanceHistoryResponse.StudentHistoryRow fila, LocalDate fecha) {
        AttendanceHistoryResponse.HistoryCell celda = fila.cellsByDate().get(fecha);
        return celda == null ? "-" : celda.status();
    }

    private Tabla tablaDeReporte(List<RosterEntryResponse> reporte) {
        List<String> encabezados = List.of("Alumno", "DNI", "Ausencias", "% Asistencia");
        List<List<String>> filas = reporte.stream()
                .map(entrada -> List.of(
                        entrada.studentName(), entrada.dni() == null ? "-" : entrada.dni(),
                        String.valueOf(entrada.absenceCount()),
                        entrada.attendancePercentage() == null ? "-" : Math.round(entrada.attendancePercentage() * 100) + "%"))
                .toList();
        return new Tabla("Reporte de ausencias", encabezados, filas);
    }

    private ExportedFile generar(Tabla tabla, String scope, String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            return new ExportedFile(generarXlsx(tabla), scope + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        return new ExportedFile(generarPdf(tabla), scope + ".pdf", "application/pdf");
    }

    private byte[] generarPdf(Tabla tabla) {
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

    private byte[] generarXlsx(Tabla tabla) {
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
