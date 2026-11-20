package com.red_escuela.backend.graduation.certificate.preview;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

@Component
public class PreviewWatermarker {

    public static final String TEXT = "VISTA PREVIA";

    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final float FONT_SIZE = 110;
    private static final float OPACITY = 0.18f;
    private static final double ROTATION_DEGREES = 35;

    public byte[] apply(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf); InputStream fontStream = PreviewWatermarker.class.getResourceAsStream(FONT_RESOURCE)) {
            PDFont font = PDType0Font.load(document, fontStream, true);
            for (PDPage page : document.getPages()) {
                stamp(document, page, font);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void stamp(PDDocument document, PDPage page, PDFont font) throws IOException {
        PDRectangle box = page.getMediaBox();
        float textWidth = font.getStringWidth(TEXT) / 1000 * FONT_SIZE;
        PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
        transparency.setNonStrokingAlphaConstant(OPACITY);
        try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            content.setGraphicsStateParameters(transparency);
            content.setNonStrokingColor(Color.RED);
            content.beginText();
            content.setFont(font, FONT_SIZE);
            content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(ROTATION_DEGREES),
                    box.getWidth() / 2 - (float) (textWidth / 2 * Math.cos(Math.toRadians(ROTATION_DEGREES))),
                    box.getHeight() / 2 - (float) (textWidth / 2 * Math.sin(Math.toRadians(ROTATION_DEGREES)))));
            content.showText(TEXT);
            content.endText();
        }
    }
}
