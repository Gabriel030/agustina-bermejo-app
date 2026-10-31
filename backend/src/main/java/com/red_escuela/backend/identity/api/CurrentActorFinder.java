package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class CurrentActorFinder {

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;

    public CurrentActorFinder(PersonRepository personRepository, StudentRepository studentRepository) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
    }

    public Long currentPersonId() {
        return Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public Person currentPerson() {
        Long personId = currentPersonId();
        return personRepository.findById(personId).orElseThrow(() -> new PersonNotFoundException(personId));
    }

    public Long currentStudentId() {
        Long personId = currentPersonId();
        return studentRepository.findByPerson_Id(personId).orElseThrow(() -> new StudentNotFoundException(personId)).getId();
    }

    public Optional<Long> findCurrentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return studentRepository.findByPerson_Id(Long.valueOf(authentication.getName())).map(student -> student.getId());
    }
}
