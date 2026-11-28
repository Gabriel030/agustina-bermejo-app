package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.IncompleteSectionEvaluationConfigException;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SectionEvaluationConfigCompletenessValidatorTest {

    private final SectionEvaluationConfigCompletenessValidator validator = new SectionEvaluationConfigCompletenessValidator(
            new SectionEvaluationSchemeLoader(mock(EvaluationSchemeRepository.class)));

    private static final LocalDate DATE = LocalDate.of(2027, 5, 10);

    private static SectionEvaluationConfig config(int exams, boolean hasFinal) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setExamCountExpected(exams);
        scheme.setHasFinal(hasFinal);
        SubjectSection section = new SubjectSection();
        section.setEvaluationScheme(scheme);
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setTpCount(2);
        config.setExam1Date(DATE);
        config.setExam2Date(DATE);
        config.setFinalCall1Date(DATE);
        return config;
    }

    @Test
    void completeConfigIsAccepted() {
        assertThatCode(() -> validator.requireComplete(config(2, true))).doesNotThrowAnyException();
    }

    @Test
    void missingCourseworkCountIsRejected() {
        SectionEvaluationConfig config = config(2, true);
        config.setTpCount(null);

        assertThatThrownBy(() -> validator.requireComplete(config))
                .isInstanceOf(IncompleteSectionEvaluationConfigException.class)
                .hasMessageContaining("tpCount");
    }

    @Test
    void zeroCourseworkCountIsAccepted() {
        SectionEvaluationConfig config = config(2, true);
        config.setTpCount(0);

        assertThatCode(() -> validator.requireComplete(config)).doesNotThrowAnyException();
    }

    @Test
    void missingExamDateWithinTheSchemeExamCountIsRejected() {
        SectionEvaluationConfig config = config(2, true);
        config.setExam2Date(null);

        assertThatThrownBy(() -> validator.requireComplete(config))
                .isInstanceOf(IncompleteSectionEvaluationConfigException.class)
                .hasMessageContaining("exam2Date");
    }

    @Test
    void examDateBeyondTheSchemeExamCountIsNotRequired() {
        SectionEvaluationConfig config = config(1, true);
        config.setExam2Date(null);

        assertThatCode(() -> validator.requireComplete(config)).doesNotThrowAnyException();
    }

    @Test
    void missingFinalDateIsRejectedOnlyWhenTheSchemeHasFinal() {
        SectionEvaluationConfig withFinal = config(2, true);
        withFinal.setFinalCall1Date(null);
        SectionEvaluationConfig withoutFinal = config(2, false);
        withoutFinal.setFinalCall1Date(null);

        assertThatThrownBy(() -> validator.requireComplete(withFinal))
                .isInstanceOf(IncompleteSectionEvaluationConfigException.class)
                .hasMessageContaining("finalCall1Date");
        assertThatCode(() -> validator.requireComplete(withoutFinal)).doesNotThrowAnyException();
    }
}
