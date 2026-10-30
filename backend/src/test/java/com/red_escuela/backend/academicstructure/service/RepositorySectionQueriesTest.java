package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySectionQueriesTest {

    @Mock
    private SubjectSectionRepository programSectionRepository;
    @Mock
    private CourseSubjectSectionRepository courseSectionRepository;
    @InjectMocks
    private RepositorySectionQueries queries;

    @Test
    void findProgramSectionThrowsWhenMissing() {
        when(programSectionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queries.findProgramSection(5L)).isInstanceOf(SectionNotFoundException.class);
    }

    @Test
    void findProgramSectionExposesSubjectTermAndCapacity() {
        when(programSectionRepository.findById(5L)).thenReturn(Optional.of(programSection(5L, 7L, 8L, 30)));

        var view = queries.findProgramSection(5L);

        assertThat(view.id()).isEqualTo(5L);
        assertThat(view.subjectId()).isEqualTo(7L);
        assertThat(view.termId()).isEqualTo(8L);
        assertThat(view.capacity()).isEqualTo(30);
        assertThat(view.name()).isEqualTo("Comision A");
    }

    @Test
    void findCourseSectionExposesCourseSubjectCourseAndTerm() {
        var course = new Course();
        course.setId(11L);
        var courseSubject = new CourseSubject();
        courseSubject.setId(12L);
        courseSubject.setCourse(course);
        var term = new Term();
        term.setId(13L);
        var section = new CourseSubjectSection();
        section.setId(6L);
        section.setName("Curso B");
        section.setCapacity(20);
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        when(courseSectionRepository.findById(6L)).thenReturn(Optional.of(section));

        var view = queries.findCourseSection(6L);

        assertThat(view.courseSubjectId()).isEqualTo(12L);
        assertThat(view.courseId()).isEqualTo(11L);
        assertThat(view.termId()).isEqualTo(13L);
        assertThat(view.capacity()).isEqualTo(20);
    }

    @Test
    void existsByKind() {
        when(programSectionRepository.existsById(1L)).thenReturn(true);
        when(courseSectionRepository.existsById(1L)).thenReturn(false);

        assertThat(queries.exists(SectionKind.PROGRAM, 1L)).isTrue();
        assertThat(queries.exists(SectionKind.COURSE, 1L)).isFalse();
    }

    @Test
    void requireExistsThrowsWhenMissing() {
        when(courseSectionRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> queries.requireExists(SectionKind.COURSE, 1L))
                .isInstanceOf(SectionNotFoundException.class);
    }

    @Test
    void lockThrowsWhenMissing() {
        when(programSectionRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queries.lockProgramSection(1L)).isInstanceOf(SectionNotFoundException.class);
        assertThatThrownBy(() -> queries.lockCourseSection(2L)).isInstanceOf(SectionNotFoundException.class);
    }

    @Test
    void findEvaluationSchemeIdReadsTheSectionOfEachKind() {
        when(programSectionRepository.existsById(5L)).thenReturn(true);
        when(programSectionRepository.findEvaluationSchemeIdById(5L)).thenReturn(Optional.of(99L));
        when(courseSectionRepository.existsById(6L)).thenReturn(true);
        when(courseSectionRepository.findEvaluationSchemeIdById(6L)).thenReturn(Optional.empty());

        assertThat(queries.findEvaluationSchemeId(SectionKind.PROGRAM, 5L)).contains(99L);
        assertThat(queries.findEvaluationSchemeId(SectionKind.COURSE, 6L)).isEmpty();
    }

    @Test
    void findEvaluationSchemeIdRejectsMissingSectionOfEachKind() {
        when(programSectionRepository.existsById(5L)).thenReturn(false);
        when(courseSectionRepository.existsById(6L)).thenReturn(false);

        assertThatThrownBy(() -> queries.findEvaluationSchemeId(SectionKind.PROGRAM, 5L)).isInstanceOf(SectionNotFoundException.class);
        assertThatThrownBy(() -> queries.findEvaluationSchemeId(SectionKind.COURSE, 6L)).isInstanceOf(SectionNotFoundException.class);
    }

    @Test
    void isEvaluationSchemeAssignedChecksBothTypes() {
        when(programSectionRepository.existsByEvaluationSchemeId(1L)).thenReturn(false);
        when(courseSectionRepository.existsByEvaluationSchemeId(1L)).thenReturn(true);
        when(programSectionRepository.existsByEvaluationSchemeId(2L)).thenReturn(true);
        when(programSectionRepository.existsByEvaluationSchemeId(3L)).thenReturn(false);
        when(courseSectionRepository.existsByEvaluationSchemeId(3L)).thenReturn(false);

        assertThat(queries.isEvaluationSchemeAssigned(1L)).isTrue();
        assertThat(queries.isEvaluationSchemeAssigned(2L)).isTrue();
        assertThat(queries.isEvaluationSchemeAssigned(3L)).isFalse();
    }

    private SubjectSection programSection(Long id, Long subjectId, Long termId, int capacity) {
        var subject = new Subject();
        subject.setId(subjectId);
        var term = new Term();
        term.setId(termId);
        var section = new SubjectSection();
        section.setId(id);
        section.setName("Comision A");
        section.setCapacity(capacity);
        section.setSubject(subject);
        section.setTerm(term);
        return section;
    }
}
