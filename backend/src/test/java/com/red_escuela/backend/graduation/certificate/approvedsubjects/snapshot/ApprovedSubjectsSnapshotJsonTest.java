package com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedSubjectsSnapshotJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void snapshotSurvivesJsonRoundTripWithoutLosingDataOrOrder() {
        ApprovedSubjectsSnapshot snapshot = new ApprovedSubjectsSnapshotFactory().create(new ApprovedSubjectsSnapshotFactory.Input(
                "PÉREZ, ANA", "DNI", "30123456", "Tecnicatura en Enfermería", "Instituto de prueba",
                List.of(new ApprovedSubjectLine("Ética", 2, new BigDecimal("7.50"), LocalDate.of(2026, 7, 1)),
                        new ApprovedSubjectLine("Anatomía", 1, new BigDecimal("8.00"), LocalDate.of(2026, 6, 1))),
                8, new ApprovedSubjectsRecipient(RecipientType.EMPLOYER, "Acme SA"), LocalDate.of(2026, 11, 5), 30,
                List.of(new SignatorySnapshot("Dirección", "Dirección académica"))));

        ApprovedSubjectsSnapshot restored = objectMapper.readValue(objectMapper.writeValueAsString(snapshot), ApprovedSubjectsSnapshot.class);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.subjects()).extracting(ApprovedSubjectLine::name).containsExactly("Anatomía", "Ética");
    }
}
