package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class CurrentInstructorIdFinder {

    private final InstructorRepository instructorRepository;

    public CurrentInstructorIdFinder(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    public Long currentInstructorId() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return instructorRepository.findByPerson_Id(personId)
                .orElseThrow(() -> new InstructorNotFoundException(personId))
                .getId();
    }
}
