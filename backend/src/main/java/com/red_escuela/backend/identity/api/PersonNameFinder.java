package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.identity.repository.PersonSpecifications;
import com.red_escuela.backend.identity.specialist.PlatformAdminProvisioner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
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
    public List<Long> findInstitutePersonIdsByNameContaining(String term, int limit) {
        return findPersonIds(PersonSpecifications.withFullNameContaining(term)
                .and(PersonSpecifications.withoutRoleNamed(PlatformAdminProvisioner.ROLE_NAME)), limit);
    }

    @Transactional(readOnly = true)
    public List<Long> findPlatformPersonIdsByNameContaining(String term, int limit) {
        return findPersonIds(PersonSpecifications.withFullNameContaining(term)
                .and(PersonSpecifications.withRoleNamed(PlatformAdminProvisioner.ROLE_NAME)), limit);
    }

    private List<Long> findPersonIds(Specification<Person> specification, int limit) {
        return personRepository.findAll(specification, PageRequest.of(0, limit, Sort.by("id"))).getContent().stream()
                .map(Person::getId).toList();
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
