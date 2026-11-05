package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionEvaluationSchemeFinderTest {

    @Mock
    private SectionFinder sectionFinder;

    @InjectMocks
    private SectionEvaluationSchemeFinder finder;

    @Test
    void summarizesTheSchemeOfAProgramSection() {
        SubjectSection section = new SubjectSection();
        section.setEvaluationScheme(scheme());
        when(sectionFinder.exists(SectionKind.PROGRAM, 3L)).thenReturn(true);
        when(sectionFinder.findProgramSectionEntity(3L)).thenReturn(section);

        assertThat(finder.findProgramSectionScheme(3L))
                .contains(new EvaluationSchemeSummary(3, 2, true, 6.0));
    }

    @Test
    void summarizesTheSchemeOfACourseSection() {
        CourseSubjectSection section = new CourseSubjectSection();
        section.setEvaluationScheme(scheme());
        when(sectionFinder.exists(SectionKind.COURSE, 4L)).thenReturn(true);
        when(sectionFinder.findCourseSectionEntity(4L)).thenReturn(section);

        assertThat(finder.findCourseSectionScheme(4L))
                .contains(new EvaluationSchemeSummary(3, 2, true, 6.0));
    }

    @Test
    void returnsEmptyWhenTheSectionHasNoScheme() {
        when(sectionFinder.exists(SectionKind.PROGRAM, 3L)).thenReturn(true);
        when(sectionFinder.findProgramSectionEntity(3L)).thenReturn(new SubjectSection());

        assertThat(finder.findProgramSectionScheme(3L)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheSectionDoesNotExist() {
        when(sectionFinder.exists(SectionKind.COURSE, 9L)).thenReturn(false);

        assertThat(finder.findCourseSectionScheme(9L)).isEmpty();
    }

    @Test
    void keepsAMissingApprovalGradeAsNull() {
        EvaluationScheme scheme = scheme();
        scheme.setMinApprovalGrade(null);
        SubjectSection section = new SubjectSection();
        section.setEvaluationScheme(scheme);
        when(sectionFinder.exists(SectionKind.PROGRAM, 3L)).thenReturn(true);
        when(sectionFinder.findProgramSectionEntity(3L)).thenReturn(section);

        assertThat(finder.findProgramSectionScheme(3L).orElseThrow().minApprovalGrade()).isNull();
    }

    private EvaluationScheme scheme() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setExamCountExpected(3);
        scheme.setTpCountExpected(2);
        scheme.setHasFinal(true);
        scheme.setMinApprovalGrade(Grade.of(new BigDecimal("6")));
        return scheme;
    }
}
