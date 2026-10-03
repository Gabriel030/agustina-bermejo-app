package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    public Optional<Student> findCurrentStudent() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Long personId = Long.valueOf(authentication.getName());
        return studentRepository.findByPerson_Id(personId);
    }
}
