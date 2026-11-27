package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionEvaluationSchemeLockedException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSectionEvaluationSchemeLockTest {

    private static final Long SECTION_ID = 5L;

    @Mock
    private CourseSubjectSectionRepository sectionRepository;
    @Mock
    private SectionEvaluationSetupPort evaluationSetupPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private CapacityChangeEvents capacityChangeEvents;
    @InjectMocks
    private CourseSectionService service;

    private CourseSubjectSection section;
    private EvaluationScheme currentScheme;

    @BeforeEach
    void setUp() {
        section = new CourseSubjectSection();
        section.setId(SECTION_ID);
        currentScheme = new EvaluationScheme();
        currentScheme.setId(10L);
        section.setEvaluationScheme(currentScheme);
        when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(section));
    }

    @Test
    void reassigningSchemeOfSectionWithGradesFailsWithConflict() {
        when(evaluationSetupPort.hasGradesLoadedInCourseSection(SECTION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(SECTION_ID, new UpdateSectionRequest(null, null, null, 11L)))
                .isInstanceOf(SectionEvaluationSchemeLockedException.class);
    }

    @Test
    void reassigningSchemeOfSectionWithoutGradesSucceeds() {
        var newScheme = new EvaluationScheme();
        newScheme.setId(11L);
        when(evaluationSetupPort.hasGradesLoadedInCourseSection(SECTION_ID)).thenReturn(false);
        when(entityManager.find(EvaluationScheme.class, 11L)).thenReturn(newScheme);
        when(sectionRepository.save(any(CourseSubjectSection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.update(SECTION_ID, new UpdateSectionRequest(null, null, null, 11L));

        assertThat(updated.getEvaluationScheme()).isSameAs(newScheme);
    }

    @Test
    void sendingTheSameSchemeOfSectionWithGradesIsAllowed() {
        when(entityManager.find(EvaluationScheme.class, 10L)).thenReturn(currentScheme);
        when(sectionRepository.save(any(CourseSubjectSection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.update(SECTION_ID, new UpdateSectionRequest(null, null, null, 10L));

        assertThat(updated.getEvaluationScheme()).isSameAs(currentScheme);
    }
}
