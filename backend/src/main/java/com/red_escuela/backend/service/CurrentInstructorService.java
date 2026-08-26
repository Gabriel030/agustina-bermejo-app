package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.exception.InstructorNotFoundException;
import com.red_escuela.backend.repository.InstructorRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resuelve el Instructor del docente autenticado a partir del "sub" del JWT (personId),
 * nunca de un id de la URL — calco de CurrentStudentService, usado por la autorización de
 * "comisión propia" y por los endpoints propios del Docente.
 */
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
