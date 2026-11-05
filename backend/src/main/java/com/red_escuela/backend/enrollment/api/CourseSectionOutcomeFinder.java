package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.CourseSectionTermEndResolver;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
@Transactional(readOnly = true)
public class CourseSectionOutcomeFinder {

    private static final Set<EnrollmentStatus> COUNTED_STATUSES = Set.of(
            EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR,
            EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED);

    private final CourseSubjectSectionEnrollmentRepository repository;
    private final CourseSectionTermEndResolver courseSectionTermEndResolver;

    public CourseSectionOutcomeFinder(CourseSubjectSectionEnrollmentRepository repository,
                                      CourseSectionTermEndResolver courseSectionTermEndResolver) {
        this.repository = repository;
        this.courseSectionTermEndResolver = courseSectionTermEndResolver;
    }

    public CourseSectionOutcomeView outcomeOf(Long courseEnrollmentId) {
        List<CourseSubjectSectionEnrollment> counted = repository.findByCourseEnrollmentId(courseEnrollmentId).stream()
                .filter(enrollment -> COUNTED_STATUSES.contains(enrollment.getStatus()))
                .toList();
        int approved = (int) counted.stream().filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.APPROVED).count();
        LocalDate lastTermEnd = counted.stream()
                .map(enrollment -> courseSectionTermEndResolver.termEndDate(enrollment.getCourseSubjectSection()))
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new CourseSectionOutcomeView(counted.size(), approved, lastTermEnd);
    }
}
