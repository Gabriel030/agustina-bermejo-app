package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDueDaysReader;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySectionEvaluationConfigDeadlineTest {

    private final InstitutionAcademicSettingsReader reader = mock(InstitutionAcademicSettingsReader.class);
    private final SectionEvaluationConfigDueDaysReader dueDaysReader = new SectionEvaluationConfigDueDaysReader(reader);

    private SectionEvaluationConfigResponse responseStartingAt(LocalDateTime start) {
        return new SectionEvaluationConfigResponse(
                1L, 2L, "Materia", "Comisión A", "DRAFT", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, start, null, null, null, null, null, null, null, null, null, null, false, false);
    }

    @Test
    void useCaseReadsTheInstitutionDueDays() {
        when(reader.read()).thenReturn(new InstitutionAcademicSettings(5, 40, false));

        int dueDays = new GetMySectionEvaluationConfigService(
                mock(SectionEvaluationConfigFinder.class), mock(SectionEvaluationConfigInstructorAccessChecker.class), dueDaysReader)
                .evaluationConfigDueDays();

        assertThat(dueDays).isEqualTo(40);
    }

    @Test
    void deadlineIsTermStartPlusDueDays() {
        var response = responseStartingAt(LocalDateTime.of(2026, 3, 2, 18, 30)).withEvaluationConfigDueDays(30);

        assertThat(response.evaluationConfigDeadline()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void deadlineChangesWithTheDueDays() {
        var start = responseStartingAt(LocalDateTime.of(2026, 3, 2, 0, 0));

        assertThat(start.withEvaluationConfigDueDays(15).evaluationConfigDeadline()).isEqualTo(LocalDate.of(2026, 3, 17));
        assertThat(start.withEvaluationConfigDueDays(7).evaluationConfigDeadline()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void deadlineIsNullWithoutTermStart() {
        assertThat(responseStartingAt(null).withEvaluationConfigDueDays(15).evaluationConfigDeadline()).isNull();
    }

    @Test
    void withoutDueDaysTheDeadlineIsNull() {
        assertThat(responseStartingAt(LocalDateTime.of(2026, 3, 2, 0, 0)).evaluationConfigDeadline()).isNull();
    }
}
