package com.red_escuela.backend.graduation.certificate.engine;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateMarkersExtraTest {

    private static final Map<String, String> EXTRAS = Map.of(
            "recipient", "Obra social OSDE",
            "average", "8,50",
            "completionPercent", "62,50",
            "subjectName", "Anatomía",
            "examDate", "12/12/2026");

    private CertificateSnapshot snapshot(Map<String, String> extras) {
        return new CertificateSnapshot("PEREZ, ANA", "DNI", "30111222", "la carrera", "Tec", null, null,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto", List.of(), "TEMPLATE_V2", null, extras);
    }

    @Test
    void recipientMarkerIsExpandedFromExtraMarkers() {
        assertThat(CertificateMarkers.expand("Ante: {recipient}", snapshot(EXTRAS), null)).isEqualTo("Ante: Obra social OSDE");
    }

    @Test
    void averageMarkerIsExpandedFromExtraMarkers() {
        assertThat(CertificateMarkers.expand("Promedio {average}", snapshot(EXTRAS), null)).isEqualTo("Promedio 8,50");
    }

    @Test
    void completionPercentMarkerIsExpandedFromExtraMarkers() {
        assertThat(CertificateMarkers.expand("{completionPercent}%", snapshot(EXTRAS), null)).isEqualTo("62,50%");
    }

    @Test
    void subjectNameMarkerIsExpandedFromExtraMarkers() {
        assertThat(CertificateMarkers.expand("{subjectName}", snapshot(EXTRAS), null)).isEqualTo("Anatomía");
    }

    @Test
    void examDateMarkerIsExpandedFromExtraMarkers() {
        assertThat(CertificateMarkers.expand("Mesa del {examDate}", snapshot(EXTRAS), null)).isEqualTo("Mesa del 12/12/2026");
    }

    @Test
    void absentExtraMarkerExpandsToEmptyText() {
        assertThat(CertificateMarkers.expand("[{average}][{recipient}]", snapshot(Map.of()), null)).isEqualTo("[][]");
    }

    @Test
    void extraMarkerValueContainingBracesIsPrintedLiterally() {
        CertificateSnapshot snapshot = snapshot(Map.of("recipient", "{holder} $1"));
        assertThat(CertificateMarkers.expand("{recipient}", snapshot, null)).isEqualTo("{holder} $1");
    }

    @Test
    void unknownMarkerStaysLiteral() {
        assertThat(CertificateMarkers.expand("{unknown}", snapshot(EXTRAS), null)).isEqualTo("{unknown}");
    }

    @Test
    void legacyConstructorYieldsEmptyImmutableExtraMarkers() {
        CertificateSnapshot legacy = new CertificateSnapshot("PEREZ, ANA", "DNI", "30111222", "la carrera", "Tec", null, null,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto", List.of(), "TEMPLATE_V2", null);
        assertThat(legacy.extraMarkers()).isEmpty();
        assertThatThrownBy(() -> legacy.extraMarkers().put("average", "9")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void extraMarkersAreCopiedDefensively() {
        Map<String, String> source = new HashMap<>(EXTRAS);
        CertificateSnapshot snapshot = snapshot(source);
        source.put("average", "1,00");
        assertThat(snapshot.extraMarkers().get("average")).isEqualTo("8,50");
    }

    @Test
    void withRecipientLabelKeepsExtraMarkers() {
        assertThat(snapshot(EXTRAS).withRecipientLabel("Trabajo").extraMarkers()).isEqualTo(EXTRAS);
    }

    @Test
    void snapshotWithoutExtraMarkersSerializesWithoutTheField() throws Exception {
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(snapshot(Map.of()));
        assertThat(json).doesNotContain("extraMarkers");
    }

    @Test
    void snapshotJsonWithoutExtraMarkersStillDeserializes() throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        String json = mapper.writeValueAsString(snapshot(Map.of()));
        CertificateSnapshot restored = mapper.readValue(json, CertificateSnapshot.class);
        assertThat(restored.extraMarkers()).isEmpty();
        assertThat(mapper.writeValueAsString(restored)).isEqualTo(json);
    }

    @Test
    void snapshotWithExtraMarkersRoundTripsThroughJson() throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        CertificateSnapshot restored = mapper.readValue(mapper.writeValueAsString(snapshot(EXTRAS)), CertificateSnapshot.class);
        assertThat(restored.extraMarkers()).isEqualTo(EXTRAS);
    }
}
