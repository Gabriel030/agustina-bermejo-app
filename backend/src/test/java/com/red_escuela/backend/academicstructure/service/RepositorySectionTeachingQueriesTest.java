package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySectionTeachingQueriesTest {

    @Mock
    private SubjectSectionInstructorRepository programInstructorRepository;
    @Mock
    private CourseSubjectSectionInstructorRepository courseInstructorRepository;
    @InjectMocks
    private RepositorySectionTeachingQueries queries;

    @Test
    void teachesReturnsTrueForProgramLink() {
        when(programInstructorRepository.existsBySectionIdAndInstructorId(10L, 3L)).thenReturn(true);

        assertThat(queries.teaches(SectionKind.PROGRAM, 10L, 3L)).isTrue();
        verifyNoInteractions(courseInstructorRepository);
    }

    @Test
    void teachesReturnsFalseForCourseLinkWithProgramKind() {
        when(programInstructorRepository.existsBySectionIdAndInstructorId(10L, 3L)).thenReturn(false);

        assertThat(queries.teaches(SectionKind.PROGRAM, 10L, 3L)).isFalse();
        verifyNoInteractions(courseInstructorRepository);
    }

    @Test
    void teachesUsesCourseLinksForCourseKind() {
        when(courseInstructorRepository.existsBySectionIdAndInstructorId(10L, 3L)).thenReturn(true);

        assertThat(queries.teaches(SectionKind.COURSE, 10L, 3L)).isTrue();
        verifyNoInteractions(programInstructorRepository);
    }

    @Test
    void requireTeachesThrowsNotOwnSection() {
        when(courseInstructorRepository.existsBySectionIdAndInstructorId(10L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> queries.requireTeaches(SectionKind.COURSE, 10L, 3L))
                .isInstanceOf(NotOwnSectionException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("10");
    }

    @Test
    void requireTeachesPassesWhenLinked() {
        when(programInstructorRepository.existsBySectionIdAndInstructorId(10L, 3L)).thenReturn(true);

        queries.requireTeaches(SectionKind.PROGRAM, 10L, 3L);
    }

    @Test
    void isLeadOnlyForLeadRole() {
        when(programInstructorRepository.existsBySectionIdAndInstructorIdAndRole(10L, 3L, InstructorRole.LEAD))
                .thenReturn(true);
        when(programInstructorRepository.existsBySectionIdAndInstructorIdAndRole(10L, 4L, InstructorRole.LEAD))
                .thenReturn(false);

        assertThat(queries.isLead(10L, 3L)).isTrue();
        assertThat(queries.isLead(10L, 4L)).isFalse();
    }
}
