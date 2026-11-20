package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class PersonAudienceFinder {

    private static final int FIND_BATCH_SIZE = 1000;

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;

    public PersonAudienceFinder(PersonRepository personRepository, StudentRepository studentRepository,
                                InstitutionRepository institutionRepository) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
    }

    public boolean institutionNameConfigured() {
        return institutionRepository.findAll().stream().findFirst().map(Institution::getName)
                .filter(name -> !name.isBlank()).isPresent();
    }

    public Set<Long> personIdsByRoleName(String roleName) {
        return personRepository.findByRolesName(roleName).stream().filter(person -> person.getStatus() == StatusType.ACTIVE).map(Person::getId).collect(Collectors.toSet());
    }

    public Set<Long> personIdsOfStudents(Collection<Long> studentIds) {
        return studentRepository.findAllById(studentIds).stream().filter(student -> student.getPerson().getStatus() == StatusType.ACTIVE).map(student -> student.getPerson().getId()).collect(Collectors.toSet());
    }

    public List<PersonAudienceView> findByIds(Collection<Long> personIds) {
        List<Long> ids = List.copyOf(personIds);
        List<PersonAudienceView> views = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += FIND_BATCH_SIZE) {
            personRepository.findAllById(ids.subList(from, Math.min(from + FIND_BATCH_SIZE, ids.size()))).stream()
                    .map(person -> new PersonAudienceView(person.getId(), person.getName() + " " + person.getLastname(),
                            person.getName(), person.getLastname(),
                            person.getEmail() == null ? null : person.getEmail().value(),
                            Boolean.TRUE.equals(person.getEmailVerified()), person.getPhoneNumber()))
                    .forEach(views::add);
        }
        return views;
    }
}
