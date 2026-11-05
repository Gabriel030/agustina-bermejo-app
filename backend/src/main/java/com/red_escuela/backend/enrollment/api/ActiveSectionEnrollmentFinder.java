package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.CourseSectionTermEndResolver;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class ActiveSectionEnrollmentFinder {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository programRepository;
    private final CourseSubjectSectionEnrollmentRepository courseRepository;
    private final CourseSectionTermEndResolver courseSectionTermEndResolver;

    public ActiveSectionEnrollmentFinder(SubjectSectionEnrollmentRepository programRepository,
                                         CourseSubjectSectionEnrollmentRepository courseRepository,
                                         CourseSectionTermEndResolver courseSectionTermEndResolver) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.courseSectionTermEndResolver = courseSectionTermEndResolver;
    }

    public List<Long> activeProgramSectionEnrollmentIds(Long programEnrollmentId) {
        return programRepository.findByProgramEnrollmentIdAndStatusIn(programEnrollmentId, ACTIVE_STATUSES).stream()
                .map(SubjectSectionEnrollment::getId)
                .toList();
    }

    public List<ActiveCourseSectionView> activeCourseSections(Long courseEnrollmentId) {
        return courseRepository.findByCourseEnrollmentIdAndStatusIn(courseEnrollmentId, ACTIVE_STATUSES).stream()
                .map(this::toView)
                .toList();
    }

    private ActiveCourseSectionView toView(CourseSubjectSectionEnrollment enrollment) {
        return new ActiveCourseSectionView(enrollment.getId(),
                courseSectionTermEndResolver.termEndDate(enrollment.getCourseSubjectSection()));
    }
}
