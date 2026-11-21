package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateCertificateRenderTest {

    private static final String URL = "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final CertificateCode CODE = new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2");

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());
    private final CertificateTemplateDefinition definition = new BuiltInCertificateTemplate()
            .resolve(GraduationCertificateKind.PROGRAM, null);

    private CertificateSnapshot snapshot(String holder, String titleName, Integer hours) {
        return new CertificateSnapshot(holder, "DNI", "30123456", "la Tecnicatura en", titleName, null, hours,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto de prueba",
                List.of(new SignatorySnapshot("Ana Firmante", "Rectora"), new SignatorySnapshot("Luis Firmante", "Secretario")),
                "TEMPLATE_V2");
    }

    private String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private CertificateAssets assets(CertificateTexts texts, byte[] logo, List<byte[]> signatures) {
        return new CertificateAssets(logo, signatures, "0000007/2026", texts);
    }

    @Test
    void markersAreExpandedInEditableTexts() throws Exception {
        CertificateTexts texts = new CertificateTexts("INSTITUTO DE PRUEBA", "DIPLOMA DE {titleKind}", "Se deja constancia de que",
                "ha aprobado {titleName} el {graduationDate}", "Dictada por la institución", "ESCANEÁ PARA VALIDAR {registryNumber}",
                "Emitido el {issuedOn} bajo el registro {registryNumber}");

        String text = textOf(renderer.render(definition, snapshot("PÉREZ, ANA", "Enfermería", 120), CODE, URL,
                assets(texts, null, List.of())));

        assertThat(text).contains("DIPLOMA DE la Tecnicatura en", "Se deja constancia de que", "ha aprobado Enfermería el 15/07/2026",
                "ESCANEÁ PARA VALIDAR 0000007/2026", "Emitido el 05/11/2026 bajo el registro 0000007/2026", "INSTITUTO DE PRUEBA",
                "Ana Firmante", "Rectora", "Luis Firmante", "Secretario");
        assertThat(text).doesNotContain("{");
    }

    @Test
    void hoursMarkerExpandsToHoursAndDictationKeepsDurationWhenMarkerIsAbsent() throws Exception {
        String withMarker = textOf(renderer.render(definition, snapshot("PÉREZ, ANA", "Enfermería", 120), CODE, URL,
                assets(new CertificateTexts(null, null, null, null, "Carga horaria: {hours}", null, null), null, List.of())));
        String withoutMarker = textOf(renderer.render(definition, snapshot("PÉREZ, ANA", "Enfermería", 120), CODE, URL,
                assets(new CertificateTexts(null, null, null, null, "Dictada y evaluada por el Instituto", null, null), null, List.of())));

        assertThat(withMarker).contains("Carga horaria: 120 horas");
        assertThat(withoutMarker).contains("con una duración de 120 horas, dictada y evaluada por el Instituto");
    }

    @Test
    void longDataIsFittedInsteadOfOverflowingOrFailing() throws Exception {
        String holder = "APELLIDO MUY LARGO DE LA PERSONA, NOMBRE COMPUESTO SUPER EXTENSO Y SEGUNDO NOMBRE ".repeat(3).strip();
        String title = "Tecnicatura Superior en Administración de Sistemas de Información Geográfica y Gestión del Territorio ".repeat(2).strip();

        byte[] pdf = renderer.render(definition, snapshot(holder, title, 1800), CODE, URL,
                assets(new CertificateTexts(null, null, null, "ha aprobado {titleKind} {titleName}", null, null, null), null, List.of()));

        assertThat(textOf(pdf)).contains("APELLIDO MUY LARGO").contains("Gestión del Territorio");
    }

    @Test
    void corruptLogoAndSignatureImagesAreOmittedWithoutFailing() throws Exception {
        byte[] garbage = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5};

        byte[] pdf = renderer.render(definition, snapshot("PÉREZ, ANA", "Enfermería", 120), CODE, URL,
                assets(CertificateTexts.DEFAULT, garbage, List.of(garbage, new byte[] {1, 2, 3})));

        String text = textOf(pdf);
        assertThat(text).contains("PÉREZ, ANA", "Ana Firmante", "Luis Firmante").contains("AB");
    }

    @Test
    void certificateWithoutTemplateTextsKeepsTheBuiltInWording() throws Exception {
        String text = textOf(renderer.render(definition, snapshot("PÉREZ, ANA", "Enfermería", 120), CODE, URL));

        assertThat(text).contains("CERTIFICADO DE APROBACIÓN", "Se certifica que", "ha aprobado la Tecnicatura en");
    }
}
