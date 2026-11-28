package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RegularCertificateTemplateTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 12, 15);

    private final RegularCertificateTemplate template = new RegularCertificateTemplate(mock(GraduationSnapshotFactory.class));

    @Test
    void recipientMarkerIsTheLabelWithoutTheIntroductoryPrefix() {
        String label = "Se extiende a pedido del interesado para ser presentado ante: Obra social, OSDE: plan 210";

        assertThat(template.extraMarkersOf(label, VALID_UNTIL)).containsEntry("recipient", "Obra social, OSDE: plan 210").hasSize(2);
    }

    @Test
    void labelWithoutSeparatorIsUsedAsIs() {
        assertThat(template.extraMarkersOf("Club Atlético", VALID_UNTIL)).containsEntry("recipient", "Club Atlético");
    }

    @Test
    void missingLabelProducesOnlyTheValidityMarker() {
        assertThat(template.extraMarkersOf(null, VALID_UNTIL)).containsOnly(Map.entry("validUntil", "15/12/2026"));
    }

    @Test
    void validityMarkerIsTheFormattedExpirationDate() {
        assertThat(template.extraMarkersOf("Club", VALID_UNTIL)).containsEntry("validUntil", "15/12/2026");
    }
}
