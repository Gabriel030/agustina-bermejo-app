package com.red_escuela.backend.graduation.certificate.engine;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class ApprovalCertificateDesign {

    public static final float PX = 0.75f;

    public static final Rect LOGO = new Rect(515, 60, 92, 92);
    public static final Rect INSTITUTION = new Rect(160, 162, 803, 20);
    public static final Rect TITLE = new Rect(160, 184, 803, 60);
    public static final Rect CERTIFIES = new Rect(160, 252, 803, 28);
    public static final Rect HOLDER = new Rect(180, 292, 763, 58);
    public static final Rect DOCUMENT = new Rect(431, 354, 261, 26);
    public static final Rect APPROVED = new Rect(160, 396, 803, 28);
    public static final Rect PROGRAM = new Rect(150, 428, 823, 46);
    public static final Rect HOURS_LINE = new Rect(160, 488, 803, 24);
    public static final Rect DATE_LINE = new Rect(160, 526, 803, 24);
    public static final Rect SIGNATORY_LEFT = new Rect(104, 596, 250, 92);
    public static final Rect SIGNATORY_RIGHT = new Rect(769, 596, 250, 92);
    public static final Rect QR = new Rect(491, 572, 140, 140);
    public static final Rect QR_LABEL = new Rect(431, 714, 260, 14);
    public static final Rect CODE = new Rect(451, 730, 220, 18);
    public static final Rect REGISTRATION = new Rect(240, 722, 230, 22);
    public static final Rect LEGEND = new Rect(690, 716, 330, 34);

    public static final String HEADLINE = "CERTIFICADO DE APROBACIÓN";
    public static final String CERTIFIES_TEXT = "Se certifica que";
    public static final String QR_LABEL_TEXT = "QR DE VALIDACIÓN DE AUTENTICIDAD";
    public static final String LEGEND_TEXT = "Certificado emitido por el Instituto Agustina Bermejo. Verificá su autenticidad escaneando el código QR.";
    public static final String MONOGRAM = "AB";

    private static final Color PAPER = new Color(0xFB, 0xF8, 0xF1);
    private static final Color NAVY = new Color(0x14, 0x21, 0x3D);
    private static final Color GOLD = new Color(0xB0, 0x8D, 0x57);
    private static final Color GOLD_DARK = new Color(0x7A, 0x5D, 0x2C);
    private static final Color INK = new Color(0x1B, 0x25, 0x40);
    private static final Color MUTED = new Color(0x4A, 0x53, 0x70);

    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre"};

    @FunctionalInterface
    public interface QrPainter {
        void paint(PDPageContentStream content, Rect pdfRect) throws IOException;
    }

    public record Fonts(PDFont sans, PDFont sansBold, PDFont serifBold) {
    }

    public static Rect toPt(Rect px) {
        return new Rect(px.x() * PX, px.y() * PX, px.width() * PX, px.height() * PX);
    }

    public static String dateLine(LocalDate date) {
        return "Se extiende el presente certificado a los " + date.getDayOfMonth() + " días del mes de "
                + MONTHS[date.getMonthValue() - 1] + " del año " + date.getYear();
    }

    public static String hoursLine(Integer hours, String institution) {
        return hours == null ? "Dictada y evaluada por " + institution
                : "con una duración de " + hours + " horas, dictada y evaluada por " + institution;
    }

    public static String documentLine(CertificateSnapshot snapshot) {
        String number = snapshot.documentNumber();
        String formatted = number.matches("\\d+") ? dotted(number) : number;
        return snapshot.documentTypeLabel() + " " + formatted;
    }

    private static String dotted(String digits) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append('.');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    public void draw(PDDocument document, PDPageContentStream content, PDRectangle page, Fonts fonts,
                     CertificateSnapshot snapshot, CertificateCode code, CertificateAssets assets, QrPainter qrPainter)
            throws IOException {
        float height = page.getHeight();
        paper(content, page);
        frames(content, page);
        CertificateTexts texts = assets.texts();
        String registry = assets.registrationNumber();
        logo(document, content, fonts, height, assets);
        text(content, height, fonts.sans(), INSTITUTION, expandedOr(texts.instituteName(), "INSTITUTO AGUSTINA BERMEJO", snapshot, registry), 13, 9, GOLD_DARK, Align.CENTER);
        text(content, height, fonts.serifBold(), TITLE, expandedOr(texts.title(), HEADLINE, snapshot, registry), 44, 24, NAVY, Align.CENTER);
        text(content, height, fonts.sans(), CERTIFIES, expandedOr(texts.intro(), CERTIFIES_TEXT, snapshot, registry), 17, 10, MUTED, Align.CENTER);
        text(content, height, fonts.serifBold(), HOLDER, snapshot.holderFullName(), 46, 22, NAVY, Align.CENTER);
        text(content, height, fonts.sans(), DOCUMENT, documentLine(snapshot), 16, 10, INK, Align.CENTER);
        text(content, height, fonts.sans(), APPROVED, expandedOr(texts.approval(), "ha aprobado " + snapshot.titleKind(), snapshot, registry), 17, 10, MUTED, Align.CENTER);
        text(content, height, fonts.serifBold(), PROGRAM, snapshot.titleName(), 34, 16, NAVY, Align.CENTER);
        text(content, height, fonts.sans(), HOURS_LINE, dictationLine(texts.dictation(), snapshot, registry), 15, 9, INK, Align.CENTER);
        text(content, height, fonts.sans(), DATE_LINE, dateLine(snapshot.issuedOn()), 15, 9, INK, Align.CENTER);
        signatories(document, content, fonts, height, snapshot.signatories(), assets.signatureImages());
        qrPainter.paint(content, pdfRect(QR, height));
        text(content, height, fonts.sans(), QR_LABEL, expandedOr(texts.qrLabel(), QR_LABEL_TEXT, snapshot, registry), 9, 6, MUTED, Align.CENTER);
        text(content, height, fonts.sansBold(), CODE, code.formatted(), 12, 7, NAVY, Align.CENTER);
        if (assets.registrationNumber() != null && !assets.registrationNumber().isBlank()) {
            text(content, height, fonts.sans(), REGISTRATION, "REG. DE CERTIF. N°: " + assets.registrationNumber(), 12, 7, INK, Align.LEFT);
        }
        text(content, height, fonts.sans(), LEGEND, expandedOr(texts.legend(), LEGEND_TEXT, snapshot, registry), 10, 7, MUTED, Align.RIGHT);
    }

    private static String expandedOr(String template, String fallback, CertificateSnapshot snapshot, String registry) {
        return template == null || template.isBlank() ? fallback : CertificateMarkers.expand(template, snapshot, registry);
    }

    private static String dictationLine(String template, CertificateSnapshot snapshot, String registry) {
        String fallback = hoursLine(snapshot.hours(), snapshot.institutionName());
        if (template == null || template.isBlank()) {
            return fallback;
        }
        String expanded = CertificateMarkers.expand(template, snapshot, registry).strip()
                .replaceAll("[\\s:,;\\-–]+$", "");
        if (expanded.isEmpty()) {
            return fallback;
        }
        if (snapshot.hours() == null || CertificateMarkers.mentionsHours(template)) {
            return expanded;
        }
        String body = CertificateMarkers.startsWithMarker(template)
                ? expanded
                : expanded.substring(0, 1).toLowerCase() + expanded.substring(1);
        return "con una duración de " + snapshot.hours() + " horas, " + body;
    }

    private static PDImageXObject imageOrNull(PDDocument document, byte[] bytes, String name) {
        if (bytes == null) {
            return null;
        }
        try {
            return PDImageXObject.createFromByteArray(document, bytes, name);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void paper(PDPageContentStream content, PDRectangle page) throws IOException {
        content.setNonStrokingColor(PAPER);
        content.addRect(0, 0, page.getWidth(), page.getHeight());
        content.fill();
    }

    private void frames(PDPageContentStream content, PDRectangle page) throws IOException {
        float outer = 28 * PX;
        float inner = 38 * PX;
        content.setStrokingColor(NAVY);
        content.setLineWidth(2 * PX);
        content.addRect(outer, outer, page.getWidth() - 2 * outer, page.getHeight() - 2 * outer);
        content.stroke();
        content.setStrokingColor(GOLD);
        content.setLineWidth(1 * PX);
        content.addRect(inner, inner, page.getWidth() - 2 * inner, page.getHeight() - 2 * inner);
        content.stroke();
        content.setStrokingColor(GOLD_DARK);
        content.setLineWidth(2 * PX);
        float arm = 22 * PX;
        float inset = 46 * PX;
        float left = inset;
        float right = page.getWidth() - inset;
        float bottom = inset;
        float top = page.getHeight() - inset;
        corner(content, left, top, arm, 1, -1);
        corner(content, right, top, arm, -1, -1);
        corner(content, left, bottom, arm, 1, 1);
        corner(content, right, bottom, arm, -1, 1);
    }

    private void corner(PDPageContentStream content, float x, float y, float arm, int dx, int dy) throws IOException {
        content.moveTo(x + dx * arm, y);
        content.lineTo(x, y);
        content.lineTo(x, y + dy * arm);
        content.stroke();
    }

    private void logo(PDDocument document, PDPageContentStream content, Fonts fonts, float pageHeight, CertificateAssets assets)
            throws IOException {
        Rect box = pdfRect(LOGO, pageHeight);
        float radius = box.width() / 2;
        float cx = box.x() + radius;
        float cy = box.y() + radius;
        PDImageXObject image = imageOrNull(document, assets.logo(), "logo");
        if (image != null) {
            content.saveGraphicsState();
            circlePath(content, cx, cy, radius - 2 * PX);
            content.clip();
            content.drawImage(image, box.x(), box.y(), box.width(), box.height());
            content.restoreGraphicsState();
        } else {
            content.setNonStrokingColor(NAVY);
            circlePath(content, cx, cy, radius - 2 * PX);
            content.fill();
            text(content, pageHeight, fonts.serifBold(), LOGO, MONOGRAM, 38, 20, PAPER, Align.CENTER);
        }
        content.setStrokingColor(GOLD);
        content.setLineWidth(2 * PX);
        circlePath(content, cx, cy, radius - PX);
        content.stroke();
    }

    private void circlePath(PDPageContentStream content, float cx, float cy, float r) throws IOException {
        float k = 0.5522847498f * r;
        content.moveTo(cx + r, cy);
        content.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        content.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        content.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        content.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        content.closePath();
    }

    private void signatories(PDDocument document, PDPageContentStream content, Fonts fonts, float pageHeight,
                             List<SignatorySnapshot> signatories, List<byte[]> images) throws IOException {
        List<Rect> slots = List.of(SIGNATORY_LEFT, SIGNATORY_RIGHT);
        for (int i = 0; i < Math.min(slots.size(), signatories.size()); i++) {
            Rect slot = slots.get(i);
            SignatorySnapshot signatory = signatories.get(i);
            PDImageXObject signature = i < images.size() ? imageOrNull(document, images.get(i), "signature") : null;
            if (signature != null) {
                signatureImage(content, pageHeight, new Rect(slot.x() + 25, slot.y() + 2, 200, 46), signature);
            }
            float lineY = pageHeight - (slot.y() + 50) * PX;
            content.setStrokingColor(GOLD);
            content.setLineWidth(1 * PX);
            content.moveTo((slot.x() + 25) * PX, lineY);
            content.lineTo((slot.x() + 225) * PX, lineY);
            content.stroke();
            text(content, pageHeight, fonts.sansBold(), signatoryName(slot), signatory.name(), 13, 8, INK, Align.CENTER);
            text(content, pageHeight, fonts.sans(), signatoryTitle(slot), signatory.title(), 11, 7, MUTED, Align.CENTER);
        }
    }

    public static Rect signatoryName(Rect slot) {
        return new Rect(slot.x(), slot.y() + 54, slot.width(), 20);
    }

    public static Rect signatoryTitle(Rect slot) {
        return new Rect(slot.x(), slot.y() + 74, slot.width(), 18);
    }

    private void signatureImage(PDPageContentStream content, float pageHeight, Rect box, PDImageXObject image)
            throws IOException {
        float scale = Math.min(box.width() * PX / image.getWidth(), box.height() * PX / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        Rect pdf = pdfRect(box, pageHeight);
        content.drawImage(image, pdf.x() + (pdf.width() - width) / 2, pdf.y() + (pdf.height() - height) / 2, width, height);
    }

    public static Rect pdfRect(Rect px, float pageHeight) {
        Rect pt = toPt(px);
        return new Rect(pt.x(), pageHeight - pt.y() - pt.height(), pt.width(), pt.height());
    }

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    public static FittedText fitted(PDFont font, Rect px, String value, float sizePx, float minPx) throws IOException {
        return TextFitter.fit(font, value, sizePx * PX, minPx * PX, px.width() * PX, px.height() * PX);
    }

    public static float lineX(PDFont font, Rect px, FittedText fitted, String line, Align align) throws IOException {
        float boxX = px.x() * PX;
        float boxW = px.width() * PX;
        float lineWidth = TextFitter.width(font, fitted.fontSize(), line);
        return switch (align) {
            case LEFT -> boxX;
            case CENTER -> boxX + (boxW - lineWidth) / 2;
            case RIGHT -> boxX + boxW - lineWidth;
        };
    }

    public static float baselineY(Rect px, float pageHeight, FittedText fitted, int index) {
        float top = pageHeight - px.y() * PX;
        float offset = (px.height() * PX - fitted.height()) / 2;
        return top - offset - index * fitted.lineHeight() - fitted.fontSize() * 0.95f;
    }

    private void text(PDPageContentStream content, float pageHeight, PDFont font, Rect px, String value, float sizePx,
                      float minPx, Color color, Align align) throws IOException {
        FittedText fitted = fitted(font, px, value, sizePx, minPx);
        content.setNonStrokingColor(color);
        for (int i = 0; i < fitted.lines().size(); i++) {
            String line = fitted.lines().get(i);
            content.beginText();
            content.setFont(font, fitted.fontSize());
            content.newLineAtOffset(lineX(font, px, fitted, line, align), baselineY(px, pageHeight, fitted, i));
            content.showText(line);
            content.endText();
        }
    }
}
