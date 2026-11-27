package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.dto.InstructorSummaryResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ExamBoardActaPdfWriter {

    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));
    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 18f;
    private static final float BODY_SIZE = 10f;
    private static final float TITLE_SIZE = 15f;
    private static final float SIGNATURE_BLOCK = 90f;
    private static final float[] WIDTHS = {28f, 230f, 80f, 90f, 87f};
    private static final List<String> HEADERS = List.of("N.°", "Apellido y nombre", "DNI", "Condición", "Nota");

    public byte[] write(ExamBoardResponse board, List<ExamBoardRegistrationSummaryResponse> registrants) {
        try (PDDocument document = new PDDocument(); InputStream fontStream = ExamBoardActaPdfWriter.class.getResourceAsStream(FONT_RESOURCE)) {
            PDFont font = PDType0Font.load(document, fontStream, true);
            PDRectangle size = PDRectangle.A4;
            PDPage page = new PDPage(size);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = drawHeading(content, font, size, board);
            y = drawRow(content, font, HEADERS, y);
            int number = 1;
            for (ExamBoardRegistrationSummaryResponse registrant : registrants) {
                if (y < MARGIN + ROW_HEIGHT) {
                    content.close();
                    page = new PDPage(size);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = drawRow(content, font, HEADERS, size.getHeight() - MARGIN - ROW_HEIGHT);
                }
                y = drawRow(content, font, rowOf(number++, registrant), y);
            }
            if (y < MARGIN + SIGNATURE_BLOCK) {
                content.close();
                page = new PDPage(size);
                document.addPage(page);
                content = new PDPageContentStream(document, page);
                y = size.getHeight() - MARGIN;
            }
            drawSignatures(content, font, size, y - 20f);
            content.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del acta de la mesa", e);
        }
    }

    private List<String> rowOf(int number, ExamBoardRegistrationSummaryResponse registrant) {
        return List.of(String.valueOf(number), dashIfBlank(registrant.studentName()), dashIfBlank(registrant.dni()),
                conditionOf(registrant), registrant.finalScore() == null ? "-" : registrant.finalScore().toPlainString());
    }

    private String conditionOf(ExamBoardRegistrationSummaryResponse registrant) {
        if (registrant.result() != null) {
            return switch (registrant.result()) {
                case PASSED -> "Aprobado";
                case FAILED -> "Desaprobado";
                case ABSENT -> "Ausente";
            };
        }
        return switch (registrant.status()) {
            case "ABSENT" -> "Ausente";
            case "WITHDRAWN" -> "Baja";
            default -> "Inscripto";
        };
    }

    private float drawHeading(PDPageContentStream content, PDFont font, PDRectangle size, ExamBoardResponse board) throws IOException {
        float y = size.getHeight() - MARGIN - TITLE_SIZE;
        text(content, font, TITLE_SIZE, MARGIN, y, "ACTA DE MESA DE EXAMEN");
        y -= 20f;
        String subject = board.subjectName() != null ? board.subjectName() : board.courseSubjectName();
        text(content, font, BODY_SIZE, MARGIN, y, "Materia: " + dashIfBlank(subject));
        y -= 14f;
        text(content, font, BODY_SIZE, MARGIN, y, "Fecha y hora: " + board.examDate().format(DATE_TIME)
                + " · Aula: " + dashIfBlank(board.classroom()));
        y -= 14f;
        text(content, font, BODY_SIZE, MARGIN, y, "Tribunal: " + (board.tribunal().isEmpty() ? "-"
                : String.join(", ", board.tribunal().stream().map(InstructorSummaryResponse::name).toList())));
        return y - 22f;
    }

    private float drawRow(PDPageContentStream content, PDFont font, List<String> cells, float y) throws IOException {
        float x = MARGIN;
        for (int column = 0; column < cells.size(); column++) {
            text(content, font, BODY_SIZE, x + 2f, y - 12f, fit(font, cells.get(column), WIDTHS[column] - 4f));
            x += WIDTHS[column];
        }
        content.moveTo(MARGIN, y - ROW_HEIGHT);
        content.lineTo(x, y - ROW_HEIGHT);
        content.stroke();
        return y - ROW_HEIGHT;
    }

    private void drawSignatures(PDPageContentStream content, PDFont font, PDRectangle size, float y)
            throws IOException {
        float lineY = y - 40f;
        float half = (size.getWidth() - 2 * MARGIN) / 2;
        content.moveTo(MARGIN + 10f, lineY);
        content.lineTo(MARGIN + half - 20f, lineY);
        content.moveTo(MARGIN + half + 10f, lineY);
        content.lineTo(MARGIN + 2 * half - 10f, lineY);
        content.stroke();
        text(content, font, BODY_SIZE, MARGIN + 10f, lineY - 14f, "Firma del tribunal");
        text(content, font, BODY_SIZE, MARGIN + half + 10f, lineY - 14f, "Firma de Secretaría Académica");
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
