package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class OfferingCurrencyFinder {
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    public OfferingCurrencyFinder(ProgramRepository programRepository, CourseRepository courseRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Currency> findProgramCurrency(Long programId) {
        return programRepository.findById(programId).map(Program::getCurrency);
    }

    @Transactional(readOnly = true)
    public Optional<Currency> findCourseCurrency(Long courseId) {
        return courseRepository.findById(courseId).map(Course::getCurrency);
    }
}
