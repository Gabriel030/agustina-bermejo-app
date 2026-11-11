package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.dto.ClosureSheetColumnResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetGradeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetStudentResponse;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClosureActaPdfWriter {

    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final float MARGIN = 30f;
    private static final float ROW_HEIGHT = 16f;
    private static final float BODY_SIZE = 8f;
    private static final float TITLE_SIZE = 14f;
    private static final float FOOTER_BLOCK = 90f;

    public byte[] write(ClosureSheetResponse sheet) {
        try (PDDocument document = new PDDocument(); InputStream fontStream = ClosureActaPdfWriter.class.getResourceAsStream(FONT_RESOURCE)) {
            PDFont font = PDType0Font.load(document, fontStream, true);
            PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            float[] widths = columnWidthsOf(sheet.columns().size(), size.getWidth() - 2 * MARGIN);
            List<String> headers = headersOf(sheet);
            PDPage page = new PDPage(size);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = drawHeading(content, font, size, sheet);
            y = drawRow(content, font, headers, widths, y, false);
            int number = 1;
            for (ClosureSheetStudentResponse student : sheet.students()) {
                if (y < MARGIN + ROW_HEIGHT) {
                    content.close();
                    page = new PDPage(size);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = size.getHeight() - MARGIN;
                    y = drawRow(content, font, headers, widths, y, false);
                }
                y = drawRow(content, font, rowOf(number++, student, sheet), widths, y,
                        student.enrollmentStatus() == EnrollmentStatus.WITHDRAWN);
            }
            if (y < MARGIN + FOOTER_BLOCK) {
                content.close();
                page = new PDPage(size);
                document.addPage(page);
                content = new PDPageContentStream(document, page);
                y = size.getHeight() - MARGIN;
            }
            drawFooter(content, font, size, sheet, y - 20f);
            content.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del acta", e);
        }
    }

    private float[] columnWidthsOf(int gradeColumns, float available) {
        float fixed = 24f + 170f + 62f + 3 * 52f + 90f;
        float grade = gradeColumns == 0 ? 0f : Math.min(90f, (available - fixed) / gradeColumns);
        float[] widths = new float[7 + gradeColumns];
        int index = 0;
        widths[index++] = 24f;
        widths[index++] = 170f;
        widths[index++] = 62f;
        for (int column = 0; column < gradeColumns; column++) {
            widths[index++] = grade;
        }
        widths[index++] = 52f;
        widths[index++] = 52f;
        widths[index++] = 52f;
        widths[index] = 90f;
        return widths;
    }

    private List<String> headersOf(ClosureSheetResponse sheet) {
        List<String> headers = new ArrayList<>(List.of("N.°", "Apellido y nombre", "DNI"));
        for (ClosureSheetColumnResponse column : sheet.columns()) {
            headers.add(column.type().displayName() + " " + column.sequenceNumber());
        }
        headers.addAll(List.of("Correlativas", "Asistencia", "Nota", "Condición"));
        return headers;
    }

    private List<String> rowOf(int number, ClosureSheetStudentResponse student, ClosureSheetResponse sheet) {
        boolean withdrawn = student.enrollmentStatus() == EnrollmentStatus.WITHDRAWN;
        List<String> cells = new ArrayList<>(List.of(String.valueOf(number), student.lastname() + ", " + student.name(),
                student.dni() == null ? "-" : student.dni()));
        for (ClosureSheetColumnResponse column : sheet.columns()) {
            cells.add(gradeLabelOf(student, column));
        }
        cells.add(yesNo(student.requirements().correlatives()));
        cells.add(yesNo(student.requirements().attendance()));
        cells.add(yesNo(student.requirements().grade()));
        cells.add(withdrawn ? "Baja" : conditionOf(student));
        return cells;
    }

    private String gradeLabelOf(ClosureSheetStudentResponse student, ClosureSheetColumnResponse column) {
        ClosureSheetGradeResponse grade = student.grades().stream()
                .filter(candidate -> candidate.type() == column.type() && candidate.sequenceNumber() == column.sequenceNumber())
                .findFirst()
                .orElse(null);
        if (grade == null || !grade.published() || grade.score() == null && grade.retakeScore() == null) {
            return "-";
        }
        if (grade.retakeScore() != null) {
            return grade.retakeScore().value().toPlainString() + " (orig. " + grade.score().value().toPlainString() + ")";
        }
        return grade.score().value().toPlainString();
    }

    private String conditionOf(ClosureSheetStudentResponse student) {
        return switch (student.suggestedResult()) {
            case PROMOTES -> "Promociona";
            case PROMOTION_CONDITIONAL, GOES_TO_FINAL -> "Va a final";
            default -> student.suggestedResult().displayName();
        };
    }

    private String yesNo(Boolean value) {
        return value == null ? "-" : value ? "Sí" : "No";
    }

    private float drawHeading(PDPageContentStream content, PDFont font, PDRectangle size, ClosureSheetResponse sheet) throws IOException {
        float y = size.getHeight() - MARGIN - TITLE_SIZE;
        text(content, font, TITLE_SIZE, MARGIN, y, "ACTA DE CIERRE DE COMISIÓN");
        y -= 18f;
        text(content, font, 10f, MARGIN, y, "Número " + sheet.reportNumber() + " · versión " + sheet.reportVersion()
                + " · Libro " + dashIfBlank(sheet.book()) + " · Folio " + dashIfBlank(sheet.folio()));
        y -= 14f;
        text(content, font, 10f, MARGIN, y, "Carrera: " + dashIfBlank(sheet.programName()) + " · Módulo: " + sheet.subjectName()
                + " · Comisión: " + sheet.sectionName());
        y -= 14f;
        text(content, font, 10f, MARGIN, y, "Cursada: " + dashIfBlank(sheet.termLabel()) + " · Docentes: "
                + sheet.instructors().stream().map(SectionInstructorSummary::name).collect(Collectors.joining(", ")));
        return y - 22f;
    }

    private float drawRow(PDPageContentStream content, PDFont font, List<String> cells, float[] widths, float y, boolean struck)
            throws IOException {
        float x = MARGIN;
        for (int column = 0; column < cells.size(); column++) {
            String value = fit(font, cells.get(column), widths[column] - 4f);
            text(content, font, BODY_SIZE, x + 2f, y - 11f, value);
            if (struck) {
                float width = font.getStringWidth(encodable(font, value)) / 1000 * BODY_SIZE;
                content.moveTo(x + 2f, y - 8f);
                content.lineTo(x + 2f + width, y - 8f);
                content.stroke();
            }
            x += widths[column];
        }
        content.moveTo(MARGIN, y - ROW_HEIGHT);
        content.lineTo(x, y - ROW_HEIGHT);
        content.stroke();
        return y - ROW_HEIGHT;
    }

    private void drawFooter(PDPageContentStream content, PDFont font, PDRectangle size, ClosureSheetResponse sheet, float y)
            throws IOException {
        text(content, font, 10f, MARGIN, y, "Total de promociones: " + sheet.summary().promote()
                + " · Total que van a final: " + sheet.summary().goToFinal());
        float lineY = y - 50f;
        float half = (size.getWidth() - 2 * MARGIN) / 2;
        content.moveTo(MARGIN + 20f, lineY);
        content.lineTo(MARGIN + half - 40f, lineY);
        content.moveTo(MARGIN + half + 20f, lineY);
        content.lineTo(MARGIN + 2 * half - 40f, lineY);
        content.stroke();
        text(content, font, BODY_SIZE, MARGIN + 20f, lineY - 12f, "Firma del docente");
        text(content, font, BODY_SIZE, MARGIN + half + 20f, lineY - 12f, "Firma de Secretaría Académica");
    }

    private String dashIfBlank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String fit(PDFont font, String value, float maxWidth) throws IOException {
        String safe = encodable(font, value);
        if (font.getStringWidth(safe) / 1000 * BODY_SIZE <= maxWidth) {
            return safe;
        }
        String trimmed = safe;
        while (!trimmed.isEmpty() && font.getStringWidth(trimmed + "...") / 1000 * BODY_SIZE > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private void text(PDPageContentStream content, PDFont font, float fontSize, float x, float y, String value) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(encodable(font, value));
        content.endText();
    }

    private String encodable(PDFont font, String value) {
        StringBuilder safe = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                safe.append(character);
            } catch (IOException | IllegalArgumentException e) {
                safe.append('?');
            }
        });
        return safe.toString();
    }
}
