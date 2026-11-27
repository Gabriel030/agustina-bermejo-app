package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateMarkers;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTemplateMarkersParityTest {

    private final DocumentTemplateMarkers markers = new DocumentTemplateMarkers();

    private final CertificateSnapshot snapshot = new CertificateSnapshot("PEREZ, ANA", "DNI", "30111222", "la carrera", "Tec",
            null, null, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto", List.of(), "TEMPLATE_V2", null,
            Map.of());

    @Test
    void everyAllowedMarkerOfEveryTypeIsExpandable() {
        for (DocumentTemplateType type : DocumentTemplateType.values()) {
            for (String marker : markers.allowedFor(type)) {
                String literal = "{" + marker + "}";
                assertThat(CertificateMarkers.expand(literal, snapshot, null))
                        .as("marker %s allowed for %s must be expanded", marker, type)
                        .isNotEqualTo(literal);
            }
        }
    }

    @Test
    void everyAllowedMarkerTakesItsValueFromTheSnapshot() {
        CertificateSnapshot withExtras = snapshot.withExtraMarkers(Map.of(
                "recipient", "r", "average", "a", "completionPercent", "c", "subjectName", "s", "examDate", "e", "validUntil", "v"));
        for (DocumentTemplateType type : DocumentTemplateType.values()) {
            for (String marker : markers.allowedFor(type)) {
                String literal = "{" + marker + "}";
                assertThat(CertificateMarkers.expand(literal, withExtras, "REG-1"))
                        .as("marker %s allowed for %s", marker, type)
                        .isNotEqualTo(literal)
                        .isNotBlank();
            }
        }
    }

    @Test
    void markersAreFilteredByType() {
        assertThat(markers.allowedFor(DocumentTemplateType.REGULAR_STUDENT_CERTIFICATE))
                .contains("recipient", "validUntil").doesNotContain("average", "completionPercent", "subjectName", "examDate");
        assertThat(markers.allowedFor(DocumentTemplateType.APPROVED_SUBJECTS_CERTIFICATE))
                .contains("average", "completionPercent").doesNotContain("recipient", "subjectName", "examDate");
        assertThat(markers.allowedFor(DocumentTemplateType.EXAM_CERTIFICATE))
                .contains("subjectName", "examDate", "recipient", "validUntil").doesNotContain("average", "completionPercent");
        assertThat(markers.allowedFor(DocumentTemplateType.EXAM_REGISTRATION_CERTIFICATE))
                .contains("subjectName", "examDate", "recipient").doesNotContain("average", "completionPercent", "validUntil");
    }
}
