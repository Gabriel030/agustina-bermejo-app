package com.red_escuela.backend.graduation.certificate.engine;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfRendererTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R";

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateTemplateDefinition template = new BuiltInCertificateTemplate()
            .resolve(GraduationCertificateKind.PROGRAM, 1L);
    private final CertificateCode code = new CertificateCode("7QK3M9XH2VBN4T8R");

    private CertificateSnapshot snapshotOf(String holder) {
        return new CertificateSnapshot(holder, "DNI", "30123456", "la carrera", "Tecnicatura en Enfermería", "UNDERGRADUATE",
                1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto de prueba",
                List.of(new SignatorySnapshot("Dirección", "Dirección académica")), BuiltInCertificateTemplate.BUILT_IN_V1);
    }

    @Test
    void pdfContainsHolderNameAndCode() throws Exception {
        byte[] pdf = renderer.render(template, snapshotOf("PÉREZ, ANA"), code, URL);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("PÉREZ, ANA").contains("7QK3-M9XH-2VBN-4T8R").contains("Tecnicatura en Enfermería");
        }
    }

    @Test
    void qrEncodesVerificationUrl() throws Exception {
        byte[] pdf = renderer.render(template, snapshotOf("PÉREZ, ANA"), code, URL);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 300);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
            assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(URL);
        }
    }

    @Test
    void nonLatinNameDoesNotFail() throws Exception {
        byte[] pdf = renderer.render(template, snapshotOf("山田, 太郎"), code, URL);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }
}
