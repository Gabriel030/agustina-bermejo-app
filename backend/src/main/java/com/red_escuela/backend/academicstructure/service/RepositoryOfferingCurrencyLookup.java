package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.OfferingCurrencyLookup;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class RepositoryOfferingCurrencyLookup implements OfferingCurrencyLookup {
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    public RepositoryOfferingCurrencyLookup(ProgramRepository programRepository, CourseRepository courseRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Currency> findProgramCurrency(Long programId) {
        return programRepository.findById(programId).map(Program::getCurrency);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Currency> findCourseCurrency(Long courseId) {
        return courseRepository.findById(courseId).map(Course::getCurrency);
    }
}
