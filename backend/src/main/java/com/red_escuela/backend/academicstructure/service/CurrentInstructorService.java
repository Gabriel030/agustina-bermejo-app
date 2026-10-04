package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentInstructorService {
    private final InstructorRepository instructorRepository;

    public CurrentInstructorService(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    public Instructor resolveCurrentInstructor() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return instructorRepository.findByPerson_Id(personId)
                .orElseThrow(() -> new InstructorNotFoundException(personId));
    }
}
