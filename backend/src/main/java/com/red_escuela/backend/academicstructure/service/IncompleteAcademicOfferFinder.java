package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CohortReadinessItemResponse;
import com.red_escuela.backend.academicstructure.dto.CourseReadinessItemResponse;
import com.red_escuela.backend.academicstructure.dto.IncompleteAcademicOfferResponse;
import com.red_escuela.backend.academicstructure.dto.IncompleteOfferItemResponse;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class IncompleteAcademicOfferFinder {

    private final ProgramCohortRepository cohortRepository;
    private final CourseRepository courseRepository;
    private final CohortReadinessChecker cohortReadinessChecker;
    private final CourseReadinessChecker courseReadinessChecker;

    public IncompleteAcademicOfferFinder(ProgramCohortRepository cohortRepository, CourseRepository courseRepository,
                                             CohortReadinessChecker cohortReadinessChecker, CourseReadinessChecker courseReadinessChecker) {
        this.cohortRepository = cohortRepository;
        this.courseRepository = courseRepository;
        this.cohortReadinessChecker = cohortReadinessChecker;
        this.courseReadinessChecker = courseReadinessChecker;
    }

    public IncompleteAcademicOfferResponse find() {
        List<IncompleteOfferItemResponse> cohorts = cohortRepository.findAll().stream()
                .map(this::incompleteCohort)
                .filter(item -> !item.missingCodes().isEmpty())
                .toList();
        List<IncompleteOfferItemResponse> courses = courseRepository.findAll().stream()
                .filter(course -> course.getIsActive() == StatusType.ACTIVE)
                .map(this::incompleteCourse)
                .filter(item -> !item.missingCodes().isEmpty())
                .toList();
        return new IncompleteAcademicOfferResponse(cohorts.size(), courses.size(), cohorts, courses);
    }

    private IncompleteOfferItemResponse incompleteCohort(ProgramCohort cohort) {
        List<CohortReadinessItemResponse> missing = cohortReadinessChecker.check(cohort.getId()).items().stream()
                .filter(item -> !item.fulfilled()).toList();
        return new IncompleteOfferItemResponse(cohort.getId(), "Cohorte " + cohort.getIntake() + " " + cohort.getCursadaStart(),
                missing.stream().map(item -> item.code().name()).toList(),
                missing.stream().flatMap(item -> item.reasons().stream()).toList());
    }

    private IncompleteOfferItemResponse incompleteCourse(Course course) {
        List<CourseReadinessItemResponse> missing = courseReadinessChecker.check(course.getId()).items().stream()
                .filter(item -> !item.fulfilled()).toList();
        return new IncompleteOfferItemResponse(course.getId(), course.getName(),
                missing.stream().map(item -> item.code().name()).toList(),
                missing.stream().flatMap(item -> item.reasons().stream()).toList());
    }
}
