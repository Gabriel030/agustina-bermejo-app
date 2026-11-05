package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TeachingTargetChecker {
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    public TeachingTargetChecker(ProgramRepository programRepository, CourseRepository courseRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public boolean programExists(Long programId) {
        return programRepository.existsById(programId);
    }

    @Transactional(readOnly = true)
    public boolean courseExists(Long courseId) {
        return courseRepository.existsById(courseId);
    }
}
