package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RegularCertificateTemplateTest {

    private final RegularCertificateTemplate template = new RegularCertificateTemplate(mock(GraduationSnapshotFactory.class));

    @Test
    void recipientMarkerIsTheLabelWithoutTheIntroductoryPrefix() {
        String label = "Se extiende a pedido del interesado para ser presentado ante: Obra social, OSDE: plan 210";

        assertThat(template.extraMarkersOf(label)).containsEntry("recipient", "Obra social, OSDE: plan 210").hasSize(1);
    }

    @Test
    void labelWithoutSeparatorIsUsedAsIs() {
        assertThat(template.extraMarkersOf("Club Atlético")).containsEntry("recipient", "Club Atlético");
    }

    @Test
    void missingLabelProducesNoMarkers() {
        assertThat(template.extraMarkersOf(null)).isEmpty();
    }
}
