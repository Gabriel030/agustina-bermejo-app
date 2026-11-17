package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class PersonAudienceFinder {

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;

    public PersonAudienceFinder(PersonRepository personRepository, StudentRepository studentRepository) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
    }

    public Set<Long> personIdsByRoleName(String roleName) {
        return personRepository.findByRolesName(roleName).stream().filter(person -> person.getStatus() == StatusType.ACTIVE).map(Person::getId).collect(Collectors.toSet());
    }

    public Set<Long> personIdsOfStudents(Collection<Long> studentIds) {
        return studentRepository.findAllById(studentIds).stream().filter(student -> student.getPerson().getStatus() == StatusType.ACTIVE).map(student -> student.getPerson().getId()).collect(Collectors.toSet());
    }

    public List<PersonAudienceView> findByIds(Collection<Long> personIds) {
        return personRepository.findAllById(personIds).stream()
                .map(person -> new PersonAudienceView(person.getId(), person.getName() + " " + person.getLastname(),
                        person.getEmail() == null ? null : person.getEmail().value(),
                        Boolean.TRUE.equals(person.getEmailVerified()), person.getPhoneNumber()))
                .toList();
    }
}
