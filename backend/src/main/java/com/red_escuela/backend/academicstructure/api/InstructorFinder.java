package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class InstructorFinder {

    private final InstructorRepository instructorRepository;

    public InstructorFinder(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    public void requireExists(Long instructorId) {
        if (!instructorRepository.existsById(instructorId)) {
            throw new InstructorNotFoundException(instructorId);
        }
    }
}
