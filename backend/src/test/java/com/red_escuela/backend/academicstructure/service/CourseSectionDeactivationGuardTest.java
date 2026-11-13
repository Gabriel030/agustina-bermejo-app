package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionHasActiveEnrollmentsException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseSectionDeactivationGuardTest {

    private final CourseSubjectSectionRepository repository = Mockito.mock(CourseSubjectSectionRepository.class);
    private final SectionEnrollmentActivityPort activityPort = Mockito.mock(SectionEnrollmentActivityPort.class);
    private final CourseSectionService service = new CourseSectionService(
            repository, null, null, null, null, null, null, null, null, null, activityPort);

    @Test
    void deactivatingACourseSectionWithLiveEnrollmentsIsRejectedAndNothingIsSaved() {
        CourseSubjectSection section = new CourseSubjectSection();
        section.setStatus(StatusType.ACTIVE);
        when(repository.findById(5L)).thenReturn(Optional.of(section));
        when(activityPort.hasLiveEnrollmentsInCourseSection(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(5L)).isInstanceOf(SectionHasActiveEnrollmentsException.class);

        assertThat(section.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(repository, never()).save(any());
    }

    @Test
    void deactivatingACourseSectionWithoutLiveEnrollmentsDeactivatesIt() {
        CourseSubjectSection section = new CourseSubjectSection();
        section.setStatus(StatusType.ACTIVE);
        when(repository.findById(5L)).thenReturn(Optional.of(section));
        when(activityPort.hasLiveEnrollmentsInCourseSection(5L)).thenReturn(false);

        service.deactivate(5L);

        assertThat(section.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(repository).save(section);
    }
}
