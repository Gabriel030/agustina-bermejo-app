package com.red_escuela.backend.graduation.certificate.exam.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.source.GraduationSnapshotFactory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExamCertificateTemplateTest {

    private final ExamCertificateTemplate template = new ExamCertificateTemplate(mock(GraduationSnapshotFactory.class));
    private final CertificateSnapshot snapshot = new CertificateSnapshot("PEREZ, ANA", "DNI", "30111222", "examen de", "Anatomía",
            null, null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), "Instituto Demo", List.of(),
            ExamCertificateTemplate.EXAM_BUILT_IN_V1, null);
    private final LocalDate validUntil = LocalDate.of(2026, 12, 5);

    @Test
    void bodyReplacesBracesInFreeTextSoPlaceholdersCannotBeInjected() {
        CertificateTemplateDefinition definition = template.definitionFor(snapshot, validUntil, RecipientType.OTHER, "{holder} {x}");

        assertThat(definition.bodyTemplate()).contains("(holder) (x)").doesNotContain("{holder} {x}");
    }

    @Test
    void bodyIncludesRecipientKindAndValidity() {
        CertificateTemplateDefinition definition = template.definitionFor(snapshot, validUntil, RecipientType.HEALTH_INSURANCE, "OSDE");

        assertThat(definition.bodyTemplate()).contains("obra social: OSDE").contains("Válido hasta 05/12/2026");
        assertThat(definition.templateRef()).isEqualTo(ExamCertificateTemplate.EXAM_BUILT_IN_V1, null);
    }

    @Test
    void blankRecipientAddsNoSentence() {
        CertificateTemplateDefinition definition = template.definitionFor(snapshot, validUntil, null, "  ");

        assertThat(definition.bodyTemplate()).doesNotContain("Se extiende");
    }
}
