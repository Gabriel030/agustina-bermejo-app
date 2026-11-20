package com.red_escuela.backend.graduation.certificate.engine;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalCertificateDesignTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final CertificateCode CODE = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");
    private static final float TOLERANCE_PX = 1.5f;

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateTemplateDefinition template = new BuiltInCertificateTemplate()
            .resolve(GraduationCertificateKind.PROGRAM, 1L);

    record Marker(String name, Rect box, java.util.function.Function<CertificateSnapshot, String> text) {
        @Override
        public String toString() {
            return name;
        }
    }

    record Word(String text, float left, float right, float top, float bottom) {
    }

    private static final String SHORT_HOLDER = "PÉREZ, ANA";
    private static final String LONG_HOLDER = "FERNÁNDEZ DE LA CRUZ Y ALVARADO MONTENEGRO, MARÍA DE LOS ÁNGELES GUADALUPE DEL CARMEN";
    private static final String SHORT_PROGRAM = "Tecnicatura en Enfermería";
    private static final String LONG_PROGRAM = "Tecnicatura Superior en Administración de Servicios de Salud con Orientación en Gestión Hospitalaria y Auditoría Médica Integral";

    private static CertificateSnapshot snapshot(String holder, String program, String institution, String signerName, String signerTitle) {
        return new CertificateSnapshot(holder, "DNI", "30123456", "la carrera", program, "UNDERGRADUATE", 1800,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), institution,
                List.of(new SignatorySnapshot(signerName, signerTitle), new SignatorySnapshot("Lic. Carlos Gómez", "Director académico")),
                BuiltInCertificateTemplate.BUILT_IN_V1);
    }

    private static List<Marker> markers() {
        return List.of(
                new Marker("institution", ApprovalCertificateDesign.INSTITUTION, s -> "INSTITUTO AGUSTINA BERMEJO"),
                new Marker("headline", ApprovalCertificateDesign.TITLE, s -> ApprovalCertificateDesign.HEADLINE),
                new Marker("certifies", ApprovalCertificateDesign.CERTIFIES, s -> ApprovalCertificateDesign.CERTIFIES_TEXT),
                new Marker("holder", ApprovalCertificateDesign.HOLDER, CertificateSnapshot::holderFullName),
                new Marker("document", ApprovalCertificateDesign.DOCUMENT, ApprovalCertificateDesign::documentLine),
                new Marker("approved", ApprovalCertificateDesign.APPROVED, s -> "ha aprobado " + s.titleKind()),
                new Marker("program", ApprovalCertificateDesign.PROGRAM, CertificateSnapshot::titleName),
                new Marker("hoursLine", ApprovalCertificateDesign.HOURS_LINE,
                        s -> ApprovalCertificateDesign.hoursLine(s.hours(), s.institutionName())),
                new Marker("dateLine", ApprovalCertificateDesign.DATE_LINE, s -> ApprovalCertificateDesign.dateLine(s.issuedOn())),
                new Marker("leftSignatoryName", ApprovalCertificateDesign.signatoryName(ApprovalCertificateDesign.SIGNATORY_LEFT),
                        s -> s.signatories().get(0).name()),
                new Marker("leftSignatoryTitle", ApprovalCertificateDesign.signatoryTitle(ApprovalCertificateDesign.SIGNATORY_LEFT),
                        s -> s.signatories().get(0).title()),
                new Marker("rightSignatoryName", ApprovalCertificateDesign.signatoryName(ApprovalCertificateDesign.SIGNATORY_RIGHT),
                        s -> s.signatories().get(1).name()),
                new Marker("rightSignatoryTitle", ApprovalCertificateDesign.signatoryTitle(ApprovalCertificateDesign.SIGNATORY_RIGHT),
                        s -> s.signatories().get(1).title()),
                new Marker("qrLabel", ApprovalCertificateDesign.QR_LABEL, s -> ApprovalCertificateDesign.QR_LABEL_TEXT),
                new Marker("code", ApprovalCertificateDesign.CODE, s -> CODE.formatted()),
                new Marker("legend", ApprovalCertificateDesign.LEGEND, s -> ApprovalCertificateDesign.LEGEND_TEXT));
    }

    static Stream<Marker> allMarkers() {
        return markers().stream();
    }

    private byte[] pdfOf(CertificateSnapshot snapshot) {
        return renderer.render(template, snapshot, CODE, URL);
    }

    private List<Word> wordsOf(byte[] pdf) throws IOException {
        List<Word> words = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    List<TextPosition> run = new ArrayList<>();
                    for (TextPosition position : positions) {
                        if (!run.isEmpty()) {
                            TextPosition previous = run.get(run.size() - 1);
                            float gap = position.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
                            if (gap > 12 || gap < -1) {
                                addRun(words, run);
                                run = new ArrayList<>();
                            }
                        }
                        run.add(position);
                    }
                    addRun(words, run);
                }
            };
            stripper.getText(document);
        }
        return words;
    }

    private static String squashed(String value) {
        return value.replaceAll("\\s+", "");
    }

    private List<Word> linesNear(List<Word> words, Rect box, String expectedText) {
        String expected = squashed(expectedText);
        return words.stream()
                .filter(word -> {
                    float centerX = (word.left() + word.right()) / 2;
                    float centerY = (word.top() + word.bottom()) / 2;
                    boolean near = centerX >= box.x() - 40 && centerX <= box.x() + box.width() + 40
                            && centerY >= box.y() - 40 && centerY <= box.y() + box.height() + 40;
                    return near && expected.contains(squashed(word.text()));
                })
                .toList();
    }

    private void assertInside(String name, Rect box, List<Word> lines) {
        assertThat(lines).as("lines of %s inside its box", name).allSatisfy(word -> {
            assertThat(word.left()).isGreaterThanOrEqualTo(box.x() - TOLERANCE_PX);
            assertThat(word.right()).isLessThanOrEqualTo(box.x() + box.width() + TOLERANCE_PX);
            assertThat(word.top()).isGreaterThanOrEqualTo(box.y() - TOLERANCE_PX);
            assertThat(word.bottom()).isLessThanOrEqualTo(box.y() + box.height() + TOLERANCE_PX);
        });
    }

    private static void addRun(List<Word> words, List<TextPosition> positions) {
        StringBuilder text = new StringBuilder();
        float left = Float.MAX_VALUE;
        float right = 0;
        float top = Float.MAX_VALUE;
        float bottom = 0;
        for (TextPosition position : positions) {
            text.append(position.getUnicode());
            float size = position.getFontSizeInPt();
            left = Math.min(left, position.getXDirAdj());
            right = Math.max(right, position.getXDirAdj() + position.getWidthDirAdj());
            top = Math.min(top, position.getYDirAdj() - size * 0.75f);
            bottom = Math.max(bottom, position.getYDirAdj() + size * 0.22f);
        }
        if (text.toString().isBlank()) {
            return;
        }
        float px = ApprovalCertificateDesign.PX;
        words.add(new Word(text.toString().trim(), left / px, right / px, top / px, bottom / px));
    }

    private void assertMarkerInsideItsBox(Marker marker, CertificateSnapshot snapshot) throws IOException {
        String expectedText = marker.text().apply(snapshot);
        List<Word> lines = linesNear(wordsOf(pdfOf(snapshot)), marker.box(), expectedText);
        String joined = lines.stream().map(Word::text).reduce("", (left, right) -> left + right);
        assertThat(squashed(joined)).as("text of %s", marker.name()).isEqualTo(squashed(expectedText));
        assertInside(marker.name(), marker.box(), lines);
    }

    @ParameterizedTest(name = "{0} stays inside its box with regular data")
    @MethodSource("allMarkers")
    void markerStaysInsideItsBoxWithRegularData(Marker marker) throws IOException {
        assertMarkerInsideItsBox(marker, snapshot(SHORT_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora"));
    }

    @ParameterizedTest(name = "{0} stays inside its box with very long data")
    @MethodSource("allMarkers")
    void markerStaysInsideItsBoxWithVeryLongData(Marker marker) throws IOException {
        assertMarkerInsideItsBox(marker, snapshot(LONG_HOLDER, LONG_PROGRAM,
                "Instituto Superior de Formación Técnica y Profesional Agustina Bermejo de Ciencias de la Salud",
                "Dra. María Laura Fernández de la Cruz y Alvarado", "Rectora y Directora Académica del Instituto Superior"));
    }

    @Test
    void registrationNumberStaysInsideItsBox() throws IOException {
        CertificateSnapshot snapshot = snapshot(SHORT_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora");
        byte[] pdf = renderer.render(template, snapshot, CODE, URL, new CertificateAssets(null, List.of(), "0001234/2026"));
        String expected = "REG. DE CERTIF. N°: 0001234/2026";

        List<Word> lines = linesNear(wordsOf(pdf), ApprovalCertificateDesign.REGISTRATION, expected);

        assertThat(lines).hasSize(1);
        assertThat(squashed(lines.get(0).text())).isEqualTo(squashed(expected));
        assertInside("registration", ApprovalCertificateDesign.REGISTRATION, lines);
    }

    @Test
    void registrationNumberIsOmittedWhenAbsent() throws IOException {
        byte[] pdf = pdfOf(snapshot(SHORT_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora"));

        assertThat(wordsOf(pdf)).noneMatch(word -> word.text().contains("REG."));
    }

    @Test
    void veryLongHolderNameIsWrappedOrShrunkWithoutLeavingItsBox() throws Exception {
        byte[] pdf = pdfOf(snapshot(LONG_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora"));

        List<Word> lines = linesNear(wordsOf(pdf), ApprovalCertificateDesign.HOLDER, LONG_HOLDER);

        assertThat(lines).hasSizeGreaterThan(1);
        assertInside("holder", ApprovalCertificateDesign.HOLDER, lines);
    }

    @Test
    void qrStaysVectorialAndDecodesToVerificationUrl() throws Exception {
        byte[] pdf = pdfOf(snapshot(SHORT_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isEmpty();
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 300);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
            assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(URL);
        }
    }

    @Test
    void qrIsPaintedInsideItsBox() throws Exception {
        byte[] pdf = pdfOf(snapshot(SHORT_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
            Rect box = ApprovalCertificateDesign.QR;
            int dark = 0;
            for (int y = (int) box.y(); y < box.y() + box.height(); y++) {
                for (int x = (int) box.x(); x < box.x() + box.width(); x++) {
                    int rgb = image.getRGB(x, y) & 0xFFFFFF;
                    if (rgb < 0x404040) {
                        dark++;
                    }
                }
            }
            assertThat(dark).isGreaterThan(1500);
            int outside = 0;
            for (int y = (int) box.y() - 12; y < box.y() - 2; y++) {
                for (int x = (int) box.x(); x < box.x() + box.width(); x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) < 0x404040) {
                        outside++;
                    }
                }
            }
            assertThat(outside).isZero();
        }
    }

    @Test
    void qrCannotBeDecodedWhenItIsRemoved() throws Exception {
        CertificatePdfRenderer withoutQr = new CertificatePdfRenderer(new QrMatrixFactory() {
            @Override
            public boolean[][] create(String content) {
                return new boolean[][]{{false}};
            }
        });
        byte[] pdf = withoutQr.render(template, snapshot(SHORT_HOLDER, SHORT_PROGRAM, "Instituto Agustina Bermejo", "Dra. Laura Pérez", "Rectora"), CODE, URL);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 300);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
            assertThatThrownBy(() -> new QRCodeReader().decode(bitmap)).isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void textFitterShrinksFontToFitWidth() throws Exception {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);

        FittedText fitted = TextFitter.fit(font, "NOMBRE EXTREMADAMENTE LARGO QUE NO ENTRA EN UNA SOLA LINEA A TAMAÑO COMPLETO", 40, 10, 300, 50);

        assertThat(fitted.fontSize()).isLessThan(40);
        assertThat(fitted.height()).isLessThanOrEqualTo(50);
        for (String line : fitted.lines()) {
            assertThat(TextFitter.width(font, fitted.fontSize(), line)).isLessThanOrEqualTo(300);
        }
    }

    @Test
    void textFitterTruncatesWithEllipsisWhenMinimumSizeStillOverflows() throws Exception {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        String value = "palabra ".repeat(200);

        FittedText fitted = TextFitter.fit(font, value, 20, 10, 200, 30);

        assertThat(fitted.height()).isLessThanOrEqualTo(30);
        assertThat(fitted.lines().get(fitted.lines().size() - 1)).endsWith("...");
    }

    @Test
    void textFitterBreaksSingleOverlongWordInsideWidth() throws Exception {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);

        FittedText fitted = TextFitter.fit(font, "A".repeat(300), 20, 10, 100, 30);

        for (String line : fitted.lines()) {
            assertThat(TextFitter.width(font, fitted.fontSize(), line)).isLessThanOrEqualTo(100);
        }
    }

    @Test
    void renderSamplePreviewWhenRequested() throws Exception {
        String target = System.getProperty("certificate.sample.png");
        if (target == null) {
            return;
        }
        byte[] pdf = pdfOf(snapshot("PÉREZ GARCÍA, ANA MARÍA", "Tecnicatura Superior en Enfermería", "Instituto Agustina Bermejo",
                "Dra. Laura Pérez", "Rectora"));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 110), "png", new File(target));
        }
    }
}
