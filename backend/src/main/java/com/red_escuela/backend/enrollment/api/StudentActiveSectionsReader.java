package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Component
@Transactional(readOnly = true)
public class StudentActiveSectionsReader {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository programRepository;
    private final CourseSubjectSectionEnrollmentRepository courseRepository;

    public StudentActiveSectionsReader(SubjectSectionEnrollmentRepository programRepository,
                                       CourseSubjectSectionEnrollmentRepository courseRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    public List<ActiveSectionView> listActiveSectionsOfStudent(Long studentId) {
        Stream<ActiveSectionView> program = programRepository
                .findWithSectionByStudentIdAndStatusIn(studentId, ACTIVE_STATUSES).stream()
                .map(enrollment -> toProgramView(enrollment.getSubjectSection()));
        Stream<ActiveSectionView> course = courseRepository
                .findWithSectionByStudentIdAndStatusIn(studentId, ACTIVE_STATUSES).stream()
                .map(enrollment -> toCourseView(enrollment.getCourseSubjectSection()));
        return Stream.concat(program, course).distinct().toList();
    }

    private ActiveSectionView toProgramView(SubjectSection section) {
        return new ActiveSectionView(SectionKind.PROGRAM, section.getId(), section.getName(),
                section.getSubject().getName(), section.getTerm() == null ? null : section.getTerm().getId());
    }

    private ActiveSectionView toCourseView(CourseSubjectSection section) {
        return new ActiveSectionView(SectionKind.COURSE, section.getId(), section.getName(),
                section.getCourseSubject().getName(), section.getTerm() == null ? null : section.getTerm().getId());
    }
}
