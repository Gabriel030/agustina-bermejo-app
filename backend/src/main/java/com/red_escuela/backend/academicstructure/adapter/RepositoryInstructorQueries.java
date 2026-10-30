package com.red_escuela.backend.academicstructure.adapter;

import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.usecase.InstructorQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RepositoryInstructorQueries implements InstructorQueries {

    private final InstructorRepository instructorRepository;

    public RepositoryInstructorQueries(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    @Override
    public void requireExists(Long instructorId) {
        if (!instructorRepository.existsById(instructorId)) {
            throw new InstructorNotFoundException(instructorId);
        }
    }
}
