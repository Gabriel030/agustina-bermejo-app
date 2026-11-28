package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SectionEvaluationSchemeLoaderTest {

    @Mock
    private EvaluationSchemeRepository schemeRepository;

    private SectionEvaluationSchemeLoader loader;

    @BeforeEach
    void createLoader() {
        loader = new SectionEvaluationSchemeLoader(schemeRepository);
    }

    @Test
    void returnsTheSchemeOfAProgramSectionWithoutQueryingWhenItIsAlreadyLoaded() {
        EvaluationScheme scheme = scheme(5L);
        SubjectSection section = new SubjectSection();
        section.setEvaluationScheme(scheme);

        assertThat(loader.ofProgramSection(section)).isSameAs(scheme);
        verifyNoInteractions(schemeRepository);
    }

    @Test
    void returnsTheSchemeOfACourseSectionWithoutQueryingWhenItIsAlreadyLoaded() {
        EvaluationScheme scheme = scheme(6L);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setEvaluationScheme(scheme);

        assertThat(loader.ofCourseSection(section)).isSameAs(scheme);
        verifyNoInteractions(schemeRepository);
    }

    @Test
    void returnsNullWhenTheSectionHasNoScheme() {
        assertThat(loader.ofProgramSection(new SubjectSection())).isNull();
        assertThat(loader.ofCourseSection(new CourseSubjectSection())).isNull();
        assertThat(loader.ofProgramSection(null)).isNull();
        verifyNoInteractions(schemeRepository);
    }

    @Test
    void resolvesTheSchemeOfAConfigThroughItsOwnerSection() {
        EvaluationScheme programScheme = scheme(1L);
        EvaluationScheme courseScheme = scheme(2L);
        SubjectSection programSection = new SubjectSection();
        programSection.setEvaluationScheme(programScheme);
        CourseSubjectSection courseSection = new CourseSubjectSection();
        courseSection.setEvaluationScheme(courseScheme);
        SectionEvaluationConfig programConfig = new SectionEvaluationConfig();
        programConfig.setSubjectSection(programSection);
        SectionEvaluationConfig courseConfig = new SectionEvaluationConfig();
        courseConfig.setCourseSection(courseSection);

        assertThat(loader.ofConfig(programConfig)).isSameAs(programScheme);
        assertThat(loader.ofConfig(courseConfig)).isSameAs(courseScheme);
    }

    private static EvaluationScheme scheme(Long id) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setId(id);
        return scheme;
    }
}
