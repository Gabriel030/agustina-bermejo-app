package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDueDaysReader;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
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
    void useCaseAppliesTheInstitutionDueDaysToTheResponse() {
        when(reader.read()).thenReturn(new InstitutionAcademicSettings(5, 40, false));
        var finder = mock(SectionEvaluationConfigFinder.class);
        var assembler = mock(SectionEvaluationConfigResponseAssembler.class);
        var config = mock(SectionEvaluationConfig.class);
        when(finder.findOrThrow(2L)).thenReturn(config);
        when(assembler.assembleForInstructor(config, 7L)).thenReturn(responseStartingAt(LocalDateTime.of(2026, 3, 2, 18, 30)));

        var response = new GetMySectionEvaluationConfigService(
                finder, mock(SectionEvaluationConfigInstructorAccessChecker.class), dueDaysReader, assembler)
                .get(new GetMySectionEvaluationConfigCommand(2L, 7L));

        assertThat(response.evaluationConfigDeadline()).isEqualTo(LocalDate.of(2026, 4, 11));
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
