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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void returnsTheSchemeOfAProgramSectionById() {
        EvaluationScheme scheme = scheme(5L);
        when(schemeRepository.findById(5L)).thenReturn(Optional.of(scheme));
        SubjectSection section = new SubjectSection();
        section.setEvaluationSchemeId(scheme == null ? null : scheme.getId());

        assertThat(loader.ofProgramSection(section)).isSameAs(scheme);
    }

    @Test
    void returnsTheSchemeOfACourseSectionById() {
        EvaluationScheme scheme = scheme(6L);
        when(schemeRepository.findById(6L)).thenReturn(Optional.of(scheme));
        CourseSubjectSection section = new CourseSubjectSection();
        section.setEvaluationSchemeId(scheme == null ? null : scheme.getId());

        assertThat(loader.ofCourseSection(section)).isSameAs(scheme);
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
        when(schemeRepository.findById(1L)).thenReturn(Optional.of(programScheme));
        when(schemeRepository.findById(2L)).thenReturn(Optional.of(courseScheme));
        SubjectSection programSection = new SubjectSection();
        programSection.setEvaluationSchemeId(programScheme == null ? null : programScheme.getId());
        CourseSubjectSection courseSection = new CourseSubjectSection();
        courseSection.setEvaluationSchemeId(courseScheme == null ? null : courseScheme.getId());
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
