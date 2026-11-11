package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosureSheetColumnResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetGradeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetStudentResponse;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClosureWorkbookWriter {

    static final String DRAFT_LEGEND = "BORRADOR, sin valor de acta";
    static final String INTERNAL_USE_LEGEND = "Uso interno";

    public byte[] write(ClosureSheetResponse sheet, ClosureViewerProfile viewer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet worksheet = workbook.createSheet("Cierre");
            CellStyle bold = boldStyle(workbook);
            CellStyle struck = struckStyle(workbook);
            int rowIndex = writeHeading(worksheet, sheet, bold);
            boolean staff = viewer.seesAdministrativeDetail();
            writeRow(worksheet, rowIndex++, headersOf(sheet, staff), bold);
            int number = 1;
            for (ClosureSheetStudentResponse student : sheet.students()) {
                boolean withdrawn = student.enrollmentStatus() == EnrollmentStatus.WITHDRAWN;
                writeRow(worksheet, rowIndex++, rowOf(number++, student, sheet.columns(), staff, withdrawn), withdrawn ? struck : null);
            }
            writeSummary(worksheet, rowIndex + 1, sheet, bold);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la planilla de cierre", e);
        }
    }

    private int writeHeading(XSSFSheet worksheet, ClosureSheetResponse sheet, CellStyle bold) {
        int rowIndex = 0;
        List<String> lines = new ArrayList<>();
        lines.add("Cierre de comisión: " + sheet.subjectName() + " · " + sheet.sectionName());
        lines.add("Carrera: " + valueOrDash(sheet.programName()) + " · Cursada: " + valueOrDash(sheet.termLabel()));
        lines.add("Docentes: " + sheet.instructors().stream().map(SectionInstructorSummary::name).collect(Collectors.joining(", ")));
        if (sheet.status() == ClosureStatus.CLOSED) {
            lines.add("Acta " + sheet.reportNumber() + " versión " + sheet.reportVersion() + " · Libro " + valueOrDash(sheet.book())
                    + " · Folio " + valueOrDash(sheet.folio()));
        } else {
            lines.add(DRAFT_LEGEND);
        }
        lines.add(INTERNAL_USE_LEGEND);
        for (int line = 0; line < lines.size(); line++) {
            writeRow(worksheet, rowIndex++, List.of(lines.get(line)), line == 0 ? bold : null);
        }
        return rowIndex + 1;
    }

    private List<String> headersOf(ClosureSheetResponse sheet, boolean staff) {
        List<String> headers = new ArrayList<>(List.of("N.°", "Apellido", "Nombre", "DNI", "Condición final"));
        for (ClosureSheetColumnResponse column : sheet.columns()) {
            String label = column.type().displayName() + " " + column.sequenceNumber();
            headers.add(label);
            headers.add(label + " (original)");
        }
        headers.addAll(List.of("Recuperatorios usados", "Promedio", "Asistencia %", "TP aprobados", "Correlativas", "Asistencia",
                "Nota", "Legajo completo"));
        if (staff) {
            headers.addAll(List.of("Pagos al día", "Documentos faltantes"));
        }
        headers.add("Observación del docente");
        return headers;
    }

    private List<String> rowOf(int number, ClosureSheetStudentResponse student, List<ClosureSheetColumnResponse> columns,
            boolean staff, boolean withdrawn) {
        List<String> cells = new ArrayList<>(List.of(String.valueOf(number), student.lastname(), student.name(),
                valueOrDash(student.dni()), withdrawn ? "Baja" : student.suggestedResult().displayName()));
        for (ClosureSheetColumnResponse column : columns) {
            ClosureSheetGradeResponse grade = gradeOf(student, column);
            cells.add(effectiveScoreOf(grade));
            cells.add(grade != null && grade.retakeScore() != null && grade.score() != null ? grade.score().value().toPlainString() : "");
        }
        cells.add(String.valueOf(student.retakesUsed()));
        cells.add(student.average() == null ? "-" : student.average().value().toPlainString());
        cells.add(student.attendancePercent() == null ? "-" : student.attendancePercent().points().toPlainString());
        cells.add(yesNo(student.requirements().coursework()));
        cells.add(yesNo(student.requirements().correlatives()));
        cells.add(yesNo(student.requirements().attendance()));
        cells.add(yesNo(student.requirements().grade()));
        cells.add(student.administrative().documentsComplete() ? "Sí" : "No");
        if (staff) {
            cells.add(paymentsLabel(student.administrative().payments()));
            cells.add(student.administrative().missingDocuments() == null ? ""
                    : String.join(", ", student.administrative().missingDocuments()));
        }
        cells.add(student.teacherObservation() == null ? "" : student.teacherObservation());
        return cells;
    }

    private void writeSummary(XSSFSheet worksheet, int rowIndex, ClosureSheetResponse sheet, CellStyle bold) {
        writeRow(worksheet, rowIndex++, List.of("Total de promociones", String.valueOf(sheet.summary().promote())), bold);
        writeRow(worksheet, rowIndex, List.of("Total que van a final", String.valueOf(sheet.summary().goToFinal())), bold);
    }

    private ClosureSheetGradeResponse gradeOf(ClosureSheetStudentResponse student, ClosureSheetColumnResponse column) {
        return student.grades().stream()
                .filter(grade -> grade.type() == column.type() && grade.sequenceNumber() == column.sequenceNumber())
                .findFirst()
                .orElse(null);
    }

    private String effectiveScoreOf(ClosureSheetGradeResponse grade) {
        if (grade == null || !grade.published()) {
            return "";
        }
        if (grade.retakeScore() != null) {
            return grade.retakeScore().value().toPlainString();
        }
        return grade.score() == null ? "" : grade.score().value().toPlainString();
    }

    private String paymentsLabel(PaymentsStanding payments) {
        return switch (payments) {
            case OK -> "Sí";
            case NOT_OK -> "No";
            case NOT_VERIFIED -> "Sin verificar";
        };
    }

    private String yesNo(Boolean value) {
        return value == null ? "-" : value ? "Sí" : "No";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void writeRow(XSSFSheet worksheet, int rowIndex, List<String> values, CellStyle style) {
        Row row = worksheet.createRow(rowIndex);
        for (int column = 0; column < values.size(); column++) {
            var cell = row.createCell(column);
            cell.setCellValue(values.get(column));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private CellStyle boldStyle(XSSFWorkbook workbook) {
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle struckStyle(XSSFWorkbook workbook) {
        XSSFFont font = workbook.createFont();
        font.setStrikeout(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }
}
