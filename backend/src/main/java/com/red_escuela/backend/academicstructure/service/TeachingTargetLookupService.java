package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.TeachingTargetLookup;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.springframework.stereotype.Component;

@Component
public class TeachingTargetLookupService implements TeachingTargetLookup {
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    public TeachingTargetLookupService(ProgramRepository programRepository, CourseRepository courseRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean programExists(Long programId) {
        return programRepository.existsById(programId);
    }

    @Override
    public boolean courseExists(Long courseId) {
        return courseRepository.existsById(courseId);
    }
}
