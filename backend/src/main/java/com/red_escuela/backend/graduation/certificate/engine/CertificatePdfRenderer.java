package com.red_escuela.backend.graduation.certificate.engine;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class CertificatePdfRenderer {

    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    public static final String ENGINE_VERSION = "1";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float QUIET_ZONE_MODULES = 2;

    private final QrMatrixFactory qrMatrixFactory;

    public CertificatePdfRenderer(QrMatrixFactory qrMatrixFactory) {
        this.qrMatrixFactory = qrMatrixFactory;
    }

    public byte[] render(CertificateTemplateDefinition definition, CertificateSnapshot snapshot, CertificateCode code,
                         String verificationUrl) {
        return render(definition, snapshot, code, verificationUrl, CertificateAssets.NONE);
    }

    public byte[] render(CertificateTemplateDefinition definition, CertificateSnapshot snapshot, CertificateCode code,
                         String verificationUrl, CertificateAssets assets) {
        if (BuiltInCertificateTemplate.BUILT_IN_V1.equals(definition.templateRef())) {
            return renderApproval(snapshot, code, verificationUrl, assets);
        }
        try (PDDocument document = new PDDocument(); InputStream fontStream = CertificatePdfRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
            PDFont font = PDType0Font.load(document, fontStream, true);
            PDRectangle size = definition.pageFormat() == PageFormat.A4_LANDSCAPE
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                    : PDRectangle.A4;
            PDPage page = new PDPage(size);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawHeader(content, font, size, snapshot, definition);
                drawBody(content, font, definition, snapshot);
                drawSignatories(content, font, definition.signatoryRect(), snapshot);
                drawQr(content, definition.qrRect(), verificationUrl);
                drawCode(content, font, definition.codeRect(), code, verificationUrl);
            }
            fixDocumentId(document, code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del certificado", e);
        }
    }

    private byte[] renderApproval(CertificateSnapshot snapshot, CertificateCode code, String verificationUrl,
                                  CertificateAssets assets) {
        try (PDDocument document = new PDDocument(); InputStream fontStream = CertificatePdfRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
            ApprovalCertificateDesign.Fonts fonts = new ApprovalCertificateDesign.Fonts(
                    PDType0Font.load(document, fontStream, true),
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                    new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD));
            PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(size);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                new ApprovalCertificateDesign().draw(document, content, size, fonts, snapshot, code, assets,
                        (stream, rect) -> drawQr(stream, rect, verificationUrl));
            }
            fixDocumentId(document, code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del certificado", e);
        }
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

    private void drawHeader(PDPageContentStream content, PDFont font, PDRectangle size, CertificateSnapshot snapshot,
                            CertificateTemplateDefinition definition) throws IOException {
        centered(content, font, 14, snapshot.institutionName(), size.getWidth(), size.getHeight() - 90);
        centered(content, font, 28, definition.headline(), size.getWidth(), size.getHeight() - 150);
    }

    private void drawBody(PDPageContentStream content, PDFont font, CertificateTemplateDefinition definition,
                          CertificateSnapshot snapshot) throws IOException {
        Rect rect = definition.bodyRect();
        float fontSize = 15;
        float leading = 24;
        float y = rect.y() + rect.height();
        for (String line : wrap(font, fontSize, rect.width(), expand(definition.bodyTemplate(), snapshot))) {
            text(content, font, fontSize, rect.x(), y, line);
            y -= leading;
        }
        if (snapshot.recipientLabel() != null) {
            for (String line : wrap(font, fontSize, rect.width(), snapshot.recipientLabel())) {
                y -= leading;
                text(content, font, fontSize, rect.x(), y, line);
            }
        }
        text(content, font, 11, rect.x(), y - 20, "Emitido el " + snapshot.issuedOn().format(DATE_FORMAT));
    }

    private void drawSignatories(PDPageContentStream content, PDFont font, Rect rect, CertificateSnapshot snapshot)
            throws IOException {
        float x = rect.x();
        for (SignatorySnapshot signatory : snapshot.signatories()) {
            content.moveTo(x, rect.y() + 40);
            content.lineTo(x + 200, rect.y() + 40);
            content.stroke();
            text(content, font, 11, x, rect.y() + 25, signatory.name());
            text(content, font, 9, x, rect.y() + 12, signatory.title());
            x += 230;
        }
    }

    private void drawQr(PDPageContentStream content, Rect rect, String verificationUrl) throws IOException {
        boolean[][] modules = qrMatrixFactory.create(verificationUrl);
        float total = modules.length + 2 * QUIET_ZONE_MODULES;
        float module = Math.min(rect.width(), rect.height()) / total;
        content.setNonStrokingColor(1f);
        content.addRect(rect.x(), rect.y(), module * total, module * total);
        content.fill();
        content.setNonStrokingColor(0f);
        for (int row = 0; row < modules.length; row++) {
            for (int column = 0; column < modules[row].length; column++) {
                if (modules[row][column]) {
                    float x = rect.x() + (column + QUIET_ZONE_MODULES) * module;
                    float y = rect.y() + (modules.length - 1 - row + QUIET_ZONE_MODULES) * module;
                    content.addRect(x, y, module, module);
                }
            }
        }
        content.fill();
    }

    private void drawCode(PDPageContentStream content, PDFont font, Rect rect, CertificateCode code,
                          String verificationUrl) throws IOException {
        text(content, font, 8, rect.x(), rect.y() + 40, "Código de verificación");
        text(content, font, 10, rect.x(), rect.y() + 27, code.formatted());
        text(content, font, 7, rect.x(), rect.y() + 14, "Verificar en: " + verificationUrl);
    }

    private String expand(String template, CertificateSnapshot snapshot) {
        String hours = snapshot.hours() == null ? "" : ", con una carga horaria de " + snapshot.hours() + " horas";
        return template
                .replace("{holder}", snapshot.holderFullName())
                .replace("{documentType}", snapshot.documentTypeLabel())
                .replace("{documentNumber}", snapshot.documentNumber())
                .replace("{titleKind}", snapshot.titleKind())
                .replace("{titleName}", snapshot.titleName())
                .replace("{hours}", hours)
                .replace("{graduationDate}", snapshot.graduationDate().format(DATE_FORMAT));
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

    private List<String> wrap(PDFont font, float fontSize, float maxWidth, String value) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : encodable(font, value).split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * fontSize > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
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
