package com.red_escuela.backend.graduation.certificate.approvedsubjects.render;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectLine;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.QrMatrixFactory;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ApprovedSubjectsPdfRenderer {

    public static final String ENGINE_VERSION = "1";

    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 16;
    private static final float BOTTOM_LIMIT = 60;
    private static final float FOOTER_HEIGHT = 260;
    private static final float QUIET_ZONE_MODULES = 2;
    private static final float QR_SIZE = 96;
    private static final float COLUMN_NAME = MARGIN;
    private static final float COLUMN_YEAR = 360;
    private static final float COLUMN_DATE = 410;
    private static final float COLUMN_GRADE = 500;
    private static final float NAME_WIDTH = 300;

    private final QrMatrixFactory qrMatrixFactory;
    private final CertificateVerificationUrl certificateVerificationUrl;

    public ApprovedSubjectsPdfRenderer(QrMatrixFactory qrMatrixFactory, CertificateVerificationUrl certificateVerificationUrl) {
        this.qrMatrixFactory = qrMatrixFactory;
        this.certificateVerificationUrl = certificateVerificationUrl;
    }

    public byte[] render(ApprovedSubjectsSnapshot snapshot, CertificateCode code) {
        String verificationUrl = certificateVerificationUrl.of(code);
        try (PDDocument document = new PDDocument(); InputStream fontStream = ApprovedSubjectsPdfRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
            PDFont font = PDType0Font.load(document, fontStream, true);
            PDRectangle size = PDRectangle.A4;
            List<String> intro = wrap(font, 11, size.getWidth() - 2 * MARGIN, introText(snapshot));
            float firstTableTop = size.getHeight() - 130 - intro.size() * 16 - 24;
            float nextTableTop = size.getHeight() - 100;
            List<List<ApprovedSubjectLine>> pages = paginate(snapshot.subjects(), firstTableTop, nextTableTop);
            int total = pages.size();
            for (int index = 0; index < total; index++) {
                PDPage page = new PDPage(size);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float tableTop = index == 0 ? firstTableTop : nextTableTop;
                    if (index == 0) {
                        drawFirstHeader(content, font, size, snapshot, intro);
                    } else {
                        drawContinuationHeader(content, font, size, snapshot);
                    }
                    float y = drawTable(content, font, size, tableTop, pages.get(index));
                    if (index == total - 1) {
                        drawFooterBlock(content, font, size, snapshot, code, verificationUrl, y);
                    }
                    drawPageNumber(content, font, size, index + 1, total, code);
                }
            }
            fixDocumentId(document, code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la constancia", e);
        }
    }

    private List<List<ApprovedSubjectLine>> paginate(List<ApprovedSubjectLine> subjects, float firstTop, float nextTop) {
        List<List<ApprovedSubjectLine>> pages = new ArrayList<>();
        List<ApprovedSubjectLine> current = new ArrayList<>();
        float top = firstTop;
        float y = top - ROW_HEIGHT;
        for (ApprovedSubjectLine line : subjects) {
            if (y - ROW_HEIGHT < BOTTOM_LIMIT) {
                pages.add(current);
                current = new ArrayList<>();
                top = nextTop;
                y = top - ROW_HEIGHT;
            }
            current.add(line);
            y -= ROW_HEIGHT;
        }
        pages.add(current);
        if (y - FOOTER_HEIGHT < BOTTOM_LIMIT) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private String introText(ApprovedSubjectsSnapshot snapshot) {
        return "Se deja constancia de que " + snapshot.holderFullName() + ", " + snapshot.documentTypeLabel() + " "
                + snapshot.documentNumber() + ", alumno/a de la carrera " + snapshot.programName()
                + ", tiene aprobadas las siguientes materias:";
    }

    private void drawFirstHeader(PDPageContentStream content, PDFont font, PDRectangle size, ApprovedSubjectsSnapshot snapshot,
                                 List<String> intro) throws IOException {
        centered(content, font, 12, snapshot.institutionName(), size.getWidth(), size.getHeight() - 60);
        centered(content, font, 18, "CONSTANCIA DE MATERIAS APROBADAS", size.getWidth(), size.getHeight() - 92);
        float y = size.getHeight() - 130;
        for (String line : intro) {
            text(content, font, 11, MARGIN, y, line);
            y -= 16;
        }
    }

    private void drawContinuationHeader(PDPageContentStream content, PDFont font, PDRectangle size,
                                        ApprovedSubjectsSnapshot snapshot) throws IOException {
        centered(content, font, 10, snapshot.institutionName(), size.getWidth(), size.getHeight() - 50);
        centered(content, font, 12, "Constancia de materias aprobadas (continuación)", size.getWidth(), size.getHeight() - 70);
    }

    private float drawTable(PDPageContentStream content, PDFont font, PDRectangle size, float top,
                            List<ApprovedSubjectLine> lines) throws IOException {
        text(content, font, 10, COLUMN_NAME, top, "Materia");
        text(content, font, 10, COLUMN_YEAR, top, "Año");
        text(content, font, 10, COLUMN_DATE, top, "Aprobada");
        text(content, font, 10, COLUMN_GRADE, top, "Nota");
        content.moveTo(MARGIN, top - 4);
        content.lineTo(size.getWidth() - MARGIN, top - 4);
        content.stroke();
        float y = top - ROW_HEIGHT - 2;
        for (ApprovedSubjectLine line : lines) {
            text(content, font, 10, COLUMN_NAME, y, fit(font, 10, NAME_WIDTH, line.name()));
            text(content, font, 10, COLUMN_YEAR, y, line.year() == null ? "-" : String.valueOf(line.year()));
            text(content, font, 10, COLUMN_DATE, y, line.approvedOn() == null ? "-" : line.approvedOn().format(DATE_FORMAT));
            text(content, font, 10, COLUMN_GRADE, y, line.grade() == null ? "-" : decimal(line.grade()));
            y -= ROW_HEIGHT;
        }
        return y;
    }

    private void drawFooterBlock(PDPageContentStream content, PDFont font, PDRectangle size, ApprovedSubjectsSnapshot snapshot,
                                 CertificateCode code, String verificationUrl, float tableEnd) throws IOException {
        float y = tableEnd - 14;
        text(content, font, 11, MARGIN, y, "Materias aprobadas: " + snapshot.approvedCount() + " de " + snapshot.totalSubjects()
                + " (" + decimal(snapshot.progressPercentage()) + " % de la carrera)");
        y -= 16;
        text(content, font, 11, MARGIN, y, "Promedio general: "
                + (snapshot.averageGrade() == null ? "no disponible" : decimal(snapshot.averageGrade())));
        y -= 16;
        if (snapshot.recipientType() != null) {
            text(content, font, 11, MARGIN, y, "Se extiende para ser presentada ante: " + recipientLabel(snapshot.recipientType()));
            y -= 16;
            for (String line : wrap(font, 11, size.getWidth() - 2 * MARGIN, snapshot.recipientText())) {
                text(content, font, 11, MARGIN, y, line);
                y -= 16;
            }
        }
        text(content, font, 11, MARGIN, y, "Emitida el " + snapshot.issuedOn().format(DATE_FORMAT) + ". Válida hasta el "
                + snapshot.validUntil().format(DATE_FORMAT) + ".");
        float qrBottom = BOTTOM_LIMIT + 20;
        drawQr(content, MARGIN, qrBottom, QR_SIZE, verificationUrl);
        float textX = MARGIN + QR_SIZE + 16;
        text(content, font, 8, textX, qrBottom + 80, "Código de verificación");
        text(content, font, 10, textX, qrBottom + 66, code.formatted());
        text(content, font, 7, textX, qrBottom + 52, "Verificar en: " + verificationUrl);
        float signatoryX = textX;
        float signatoryY = qrBottom + 24;
        for (SignatorySnapshot signatory : snapshot.signatories()) {
            content.moveTo(signatoryX, signatoryY + 10);
            content.lineTo(signatoryX + 150, signatoryY + 10);
            content.stroke();
            text(content, font, 9, signatoryX, signatoryY, signatory.name());
            text(content, font, 7, signatoryX, signatoryY - 10, signatory.title());
            signatoryX += 170;
        }
    }

    private void drawPageNumber(PDPageContentStream content, PDFont font, PDRectangle size, int page, int total,
                                CertificateCode code) throws IOException {
        centered(content, font, 8, "Página " + page + " de " + total + " - " + code.formatted(), size.getWidth(), 30);
    }

    private void drawQr(PDPageContentStream content, float x, float y, float side, String verificationUrl) throws IOException {
        boolean[][] modules = qrMatrixFactory.create(verificationUrl);
        float totalModules = modules.length + 2 * QUIET_ZONE_MODULES;
        float module = side / totalModules;
        content.setNonStrokingColor(1f);
        content.addRect(x, y, module * totalModules, module * totalModules);
        content.fill();
        content.setNonStrokingColor(0f);
        for (int row = 0; row < modules.length; row++) {
            for (int column = 0; column < modules[row].length; column++) {
                if (modules[row][column]) {
                    content.addRect(x + (column + QUIET_ZONE_MODULES) * module,
                            y + (modules.length - 1 - row + QUIET_ZONE_MODULES) * module, module, module);
                }
            }
        }
        content.fill();
    }

    private String recipientLabel(RecipientType type) {
        return switch (type) {
            case HEALTH_INSURANCE -> "Obra social";
            case EMPLOYER -> "Empleador";
            case STUDENT_TRANSPORT -> "Transporte estudiantil";
            case OTHER -> "Otro";
        };
    }

    private String decimal(BigDecimal value) {
        return value.toPlainString().replace('.', ',');
    }

    private void fixDocumentId(PDDocument document, CertificateCode code) {
        try {
            COSString id = new COSString(MessageDigest.getInstance("MD5").digest(code.value().getBytes(StandardCharsets.UTF_8)));
            COSArray ids = new COSArray();
            ids.add(id);
            ids.add(id);
            document.getDocument().getTrailer().setItem(COSName.ID, ids);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 no disponible", e);
        }
    }

    private void centered(PDPageContentStream content, PDFont font, float fontSize, String value, float pageWidth, float y)
            throws IOException {
        String safe = encodable(font, value);
        float width = font.getStringWidth(safe) / 1000 * fontSize;
        text(content, font, fontSize, (pageWidth - width) / 2, y, safe);
    }

    private void text(PDPageContentStream content, PDFont font, float fontSize, float x, float y, String value)
            throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(encodable(font, value));
        content.endText();
    }

    private String fit(PDFont font, float fontSize, float maxWidth, String value) throws IOException {
        String safe = encodable(font, value);
        if (font.getStringWidth(safe) / 1000 * fontSize <= maxWidth) {
            return safe;
        }
        String trimmed = safe;
        while (!trimmed.isEmpty() && font.getStringWidth(trimmed + "...") / 1000 * fontSize > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private List<String> wrap(PDFont font, float fontSize, float maxWidth, String value) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : encodable(font, value).split(" ")) {
            for (String piece : splitToWidth(font, fontSize, maxWidth, word)) {
                String candidate = current.isEmpty() ? piece : current + " " + piece;
                if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * fontSize > maxWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(piece);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> splitToWidth(PDFont font, float fontSize, float maxWidth, String word) throws IOException {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            if (!piece.isEmpty() && font.getStringWidth(piece.toString() + word.charAt(index)) / 1000 * fontSize > maxWidth) {
                pieces.add(piece.toString());
                piece = new StringBuilder();
            }
            piece.append(word.charAt(index));
        }
        pieces.add(piece.toString());
        return pieces;
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
