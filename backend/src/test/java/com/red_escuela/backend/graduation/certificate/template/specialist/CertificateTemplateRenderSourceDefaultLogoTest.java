package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateAssets;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.DefaultInstituteLogo;
import com.red_escuela.backend.graduation.certificate.engine.QrMatrixFactory;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateImage;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateImageRepository;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateTemplateRenderSourceDefaultLogoTest {

    private static final Long TEMPLATE_ID = 7L;
    private static final Long OWN_LOGO_ID = 90L;

    private final CertificateTemplateImageRepository imageRepository = mock(CertificateTemplateImageRepository.class);
    private final CertificateTemplateRenderSource source = new CertificateTemplateRenderSource(
            mock(CertificateTemplateFinder.class), mock(CertificateTemplateVersionRepository.class), imageRepository);
    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer(new QrMatrixFactory());

    private CertificateTemplateVersion versionWithLogo(Long logoImageId) {
        return CertificateTemplateVersion.builder().templateId(TEMPLATE_ID).versionNumber(1).logoImageId(logoImageId).build();
    }

    private byte[] renderWith(CertificateAssets assets) {
        CertificateSnapshot snapshot = new CertificateSnapshot("PÉREZ, ANA", "DNI", "30123456", "la carrera", "Tecnicatura",
                "UNDERGRADUATE", 1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto de prueba",
                List.of(new SignatorySnapshot("Dirección", "Dirección académica")), "TEMPLATE_V1", null);
        return renderer.render(new BuiltInCertificateTemplate().resolve(GraduationCertificateKind.PROGRAM, 1L), snapshot,
                new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2"), "https://app.example/verificar/7QK3M9XH2VBN4T8R5W6YZ0A1B2", assets);
    }

    private long imagesIn(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDResources resources = document.getPage(0).getResources();
            long count = 0;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Test
    void givenTemplateWithoutLogo_whenAssetsAreBuilt_thenLogoIsTheDefaultInstituteLogo() {
        CertificateAssets assets = source.assetsOf(versionWithLogo(null), "REG-1");

        assertThat(assets.logo()).isEqualTo(DefaultInstituteLogo.bytes());
    }

    @Test
    void givenTemplateLogoMissingInStorage_whenAssetsAreBuilt_thenLogoIsTheDefaultInstituteLogo() {
        when(imageRepository.findByIdAndTemplateId(OWN_LOGO_ID, TEMPLATE_ID)).thenReturn(Optional.empty());

        CertificateAssets assets = source.assetsOf(versionWithLogo(OWN_LOGO_ID), "REG-1");

        assertThat(assets.logo()).isEqualTo(DefaultInstituteLogo.bytes());
    }

    @Test
    void givenTemplateWithOwnLogo_whenAssetsAreBuilt_thenLogoIsTheOwnOne() {
        byte[] ownLogo = {1, 2, 3};
        CertificateTemplateImage image = CertificateTemplateImage.builder().content(ownLogo).build();
        when(imageRepository.findByIdAndTemplateId(OWN_LOGO_ID, TEMPLATE_ID)).thenReturn(Optional.of(image));

        CertificateAssets assets = source.assetsOf(versionWithLogo(OWN_LOGO_ID), "REG-1");

        assertThat(assets.logo()).isEqualTo(ownLogo);
    }

    @Test
    void givenTemplateWithoutLogo_whenPdfIsRendered_thenPageContainsTheDefaultLogoImage() throws Exception {
        byte[] pdf = renderWith(source.assetsOf(versionWithLogo(null), "REG-1"));

        assertThat(imagesIn(pdf)).isEqualTo(1);
    }

    @Test
    void givenNoLogoAtAll_whenPdfIsRendered_thenPageContainsNoImage() throws Exception {
        byte[] pdf = renderWith(CertificateAssets.NONE);

        assertThat(imagesIn(pdf)).isZero();
    }
}
