package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PersonNameFinder {

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;

    public PersonNameFinder(PersonRepository personRepository, StudentRepository studentRepository) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findFullNamesByPersonId(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return personRepository.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> person.getName() + " " + person.getLastname(), (first, second) -> first));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findFullNamesByStudentId(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, student -> student.getPerson().getName() + " " + student.getPerson().getLastname(), (first, second) -> first));
    }
}
