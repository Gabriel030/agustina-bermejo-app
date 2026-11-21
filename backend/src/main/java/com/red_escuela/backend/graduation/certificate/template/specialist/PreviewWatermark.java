package com.red_escuela.backend.graduation.certificate.template.specialist;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Component
public class PreviewWatermark {

    static final String TEXT = "VISTA PREVIA";

    public byte[] stamp(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (PDPage page : document.getPages()) {
                float width = page.getMediaBox().getWidth();
                float height = page.getMediaBox().getHeight();
                float fontSize = width / 6;
                float textWidth = font.getStringWidth(TEXT) / 1000 * fontSize;
                PDExtendedGraphicsState translucent = new PDExtendedGraphicsState();
                translucent.setNonStrokingAlphaConstant(0.25f);
                try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    content.setGraphicsStateParameters(translucent);
                    content.setNonStrokingColor(Color.RED);
                    content.beginText();
                    content.setFont(font, fontSize);
                    content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(30),
                            (width - textWidth * 0.866f) / 2, height / 2 - textWidth * 0.25f));
                    content.showText(TEXT);
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo marcar el PDF de vista previa", e);
        }
    }
}
