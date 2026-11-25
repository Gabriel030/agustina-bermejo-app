package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.policy.LegalAge;
import com.red_escuela.backend.identity.repository.PersonBirthDateRow;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MinorGuardianEmailCollisionFinder {

    private static final int LOOKUP_CHUNK_SIZE = 500;

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;

    public MinorGuardianEmailCollisionFinder(PersonRepository personRepository, StudentRepository studentRepository) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Set<Long> findMinorPersonIdsWithEmailSharedWithGuardian(Collection<Long> personIds) {
        List<Long> minorIds = findMinorPersonIds(personIds.stream().distinct().toList());
        Set<Long> colliding = new HashSet<>();
        for (int from = 0; from < minorIds.size(); from += LOOKUP_CHUNK_SIZE) {
            colliding.addAll(studentRepository.findPersonIdsWithEmailEqualToAnyGuardianEmail(minorIds.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, minorIds.size()))));
        }
        return colliding;
    }

    private List<Long> findMinorPersonIds(List<Long> ids) {
        LocalDate today = ApplicationTime.today();
        LocalDate looseCutoff = today.minusYears(LegalAge.ADULT_YEARS).minusDays(1);
        List<Long> minors = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += LOOKUP_CHUNK_SIZE) {
            for (PersonBirthDateRow row : personRepository.findBirthDatesNewerThan(ids.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, ids.size())), looseCutoff)) {
                if (LegalAge.isMinor(row.getBirthDate(), today)) {
                    minors.add(row.getId());
                }
            }
        }
        return minors;
    }
}
