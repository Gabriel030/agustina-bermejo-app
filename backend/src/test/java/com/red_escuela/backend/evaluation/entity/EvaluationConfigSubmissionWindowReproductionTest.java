package com.red_escuela.backend.evaluation.entity;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationConfigSubmissionWindowReproductionTest {

    private static final LocalDateTime TERM_START = LocalDateTime.of(2027, 3, 1, 0, 0);
    private static final int DUE_DAYS = 15;

    private static SectionEvaluationConfig draftConfig() {
        Term term = new Term();
        term.setStartDate(TERM_START);
        term.setEndDate(TERM_START.plusMonths(4));
        Subject subject = new Subject();
        subject.setName("Matematica");
        SubjectSection section = new SubjectSection();
        section.setTerm(term);
        section.setSubject(subject);
        section.setName("A");
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setStatus(SectionEvaluationConfigStatus.DRAFT);
        return config;
    }

    @Test
    void submissionRemainsOpenUntilTheAnnouncedEvaluationConfigDeadline() {
        SectionEvaluationConfig config = draftConfig();
        LocalDate announcedDeadline = SectionEvaluationConfigResponse.from(config, List.of())
                .withEvaluationConfigDueDays(DUE_DAYS).evaluationConfigDeadline();
        LocalDateTime insideAnnouncedWindow = TERM_START.plusDays(5).withHour(10);

        assertThat(announcedDeadline).isEqualTo(TERM_START.toLocalDate().plusDays(DUE_DAYS));
        assertThat(insideAnnouncedWindow.toLocalDate()).isBeforeOrEqualTo(announcedDeadline);
        assertThat(config.withEvaluationConfigDueDays(DUE_DAYS).canBeSubmitted(insideAnnouncedWindow)).isTrue();
    }

    @Test
    void submissionWindowClosesTheDayAfterTheAnnouncedDeadline() {
        SectionEvaluationConfig config = draftConfig();
        LocalDateTime lastDay = TERM_START.plusDays(DUE_DAYS).withHour(23).withMinute(59);

        assertThat(config.withEvaluationConfigDueDays(DUE_DAYS).canBeSubmitted(lastDay)).isTrue();
        assertThat(config.canBeSubmitted(lastDay.plusMinutes(1))).isFalse();
    }
}
