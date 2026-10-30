package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class CourseCatalogFinder {

    private final CourseRepository courseRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;

    public CourseCatalogFinder(CourseRepository courseRepository,
                               CourseSubjectRepository courseSubjectRepository,
                               CourseSubjectSectionRepository courseSectionRepository) {
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    public Course findCourseEntity(Long courseId) {
        return courseRepository.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    public CourseRegistrationView findRegistration(Long courseId, LocalDateTime moment) {
        Course course = findCourseEntity(courseId);
        boolean windowInverted = course.getRegistrationStart() != null && course.getRegistrationEnd() != null
                && course.getRegistrationStart().isAfter(course.getRegistrationEnd());
        return new CourseRegistrationView(courseId, course.getTerm().getId(), windowInverted,
                course.isRegistrationOpenAt(moment));
    }

    public boolean hasCapacityFor(Long courseId, long occupiedSeats) {
        return findCourseEntity(courseId).hasCapacityFor(occupiedSeats);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCourse(Long courseId) {
        courseRepository.findByIdForUpdate(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    public List<CourseModuleView> findActiveModules(Long courseId) {
        return courseSubjectRepository.findByCourseIdAndStatusOrderBySortOrder(courseId, StatusType.ACTIVE).stream()
                .map(this::toModuleView)
                .toList();
    }

    public List<CourseSectionView> findActiveSectionsOfModule(Long courseSubjectId) {
        return courseSectionRepository.findByCourseSubjectIdAndStatus(courseSubjectId, StatusType.ACTIVE).stream()
                .map(this::toSectionView)
                .toList();
    }

    public List<Long> findCourseIdsWithWaitlistDeadlinePassed(LocalDate today) {
        return courseRepository.findByWaitlistDeadlineOffsetDaysIsNotNull().stream()
                .filter(course -> course.waitlistDeadlineHasPassed(today))
                .map(Course::getId)
                .toList();
    }

    private CourseModuleView toModuleView(CourseSubject courseSubject) {
        return new CourseModuleView(courseSubject.getId(), courseSubject.getName());
    }

    private CourseSectionView toSectionView(CourseSubjectSection section) {
        return new CourseSectionView(section.getId(), section.getCourseSubject().getId(),
                section.getCourseSubject().getCourse().getId(), section.getTerm().getId(), section.getCapacity(),
                section.getName());
    }
}
