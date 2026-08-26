package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resuelve el Student del alumno autenticado a partir del "sub" del JWT (personId),
 * nunca de un id de la URL — mismo mecanismo que PersonProfileService.resolveAuthenticatedPerson,
 * usado por todos los endpoints /me/... de esta spec.
 */
@Service
public class CurrentStudentService {

    private final StudentRepository studentRepository;

    public CurrentStudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student resolveCurrentStudent() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return studentRepository.findByPerson_Id(personId)
                .orElseThrow(() -> new StudentNotFoundException(personId));
    }
}
