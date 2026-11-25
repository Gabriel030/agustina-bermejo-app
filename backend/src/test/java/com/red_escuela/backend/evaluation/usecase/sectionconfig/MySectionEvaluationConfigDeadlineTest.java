package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySectionEvaluationConfigDeadlineTest {

    private final SectionEvaluationConfigRepository repository = mock(SectionEvaluationConfigRepository.class);
    private final SectionEvaluationConfigFinder finder = mock(SectionEvaluationConfigFinder.class);
    private final SectionEvaluationConfigInstructorAccessChecker access = mock(SectionEvaluationConfigInstructorAccessChecker.class);
    private final InstitutionAcademicSettingsReader reader = mock(InstitutionAcademicSettingsReader.class);

    private SectionEvaluationConfig configStartingAt(LocalDateTime start) {
        Term term = new Term();
        term.setStartDate(start);
        SubjectSection section = new SubjectSection();
        section.setTerm(term);
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        return config;
    }

    private void institutionDueDays(int days) {
        when(reader.read()).thenReturn(new InstitutionAcademicSettings(5, days, false));
    }

    @Test
    void deadlineIsTermStartPlusInstitutionDueDays() {
        institutionDueDays(30);

        var result = SectionEvaluationConfigWithDeadline.of(configStartingAt(LocalDateTime.of(2026, 3, 2, 18, 30)), 30);

        assertThat(result.deadline()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void deadlineIsNullWhenThereIsNoTermStart() {
        var result = SectionEvaluationConfigWithDeadline.of(configStartingAt(null), 15);

        assertThat(result.deadline()).isNull();
    }

    @Test
    void listUsesTheInstitutionDueDaysForEveryConfig() {
        institutionDueDays(7);
        SectionEvaluationConfig first = configStartingAt(LocalDateTime.of(2026, 3, 2, 0, 0));
        SectionEvaluationConfig second = configStartingAt(LocalDateTime.of(2026, 8, 3, 0, 0));
        when(repository.findBySubjectSection_Instructors_InstructorId(9L)).thenReturn(List.of(first, second));

        var result = new ListMySectionEvaluationConfigsService(repository, reader).list(9L);

        assertThat(result).extracting(SectionEvaluationConfigWithDeadline::deadline)
                .containsExactly(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 8, 10));
        assertThat(result).extracting(SectionEvaluationConfigWithDeadline::config).containsExactly(first, second);
    }

    @Test
    void listHasNoDeadlineForAConfigWithoutTermStart() {
        institutionDueDays(15);
        when(repository.findBySubjectSection_Instructors_InstructorId(9L)).thenReturn(List.of(configStartingAt(null)));

        var result = new ListMySectionEvaluationConfigsService(repository, reader).list(9L);

        assertThat(result).singleElement().extracting(SectionEvaluationConfigWithDeadline::deadline).isNull();
    }

    @Test
    void getUsesTheInstitutionDueDays() {
        institutionDueDays(20);
        SectionEvaluationConfig config = configStartingAt(LocalDateTime.of(2026, 3, 2, 0, 0));
        when(finder.findOrThrow(4L)).thenReturn(config);

        var result = new GetMySectionEvaluationConfigService(finder, access, reader)
                .get(new GetMySectionEvaluationConfigCommand(4L, 9L));

        assertThat(result.deadline()).isEqualTo(LocalDate.of(2026, 3, 22));
        assertThat(result.config()).isSameAs(config);
    }
}
