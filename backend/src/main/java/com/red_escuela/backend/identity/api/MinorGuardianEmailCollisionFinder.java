package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.policy.LegalAge;
import com.red_escuela.backend.identity.repository.PersonBirthDateRow;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class MinorGuardianEmailCollisionFinder {

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;

    public MinorGuardianEmailCollisionFinder(PersonRepository personRepository, StudentRepository studentRepository) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Set<Long> findMinorPersonIdsWithEmailSharedWithGuardian(Collection<Long> personIds) {
        Long[] distinctIds = personIds.stream().distinct().toArray(Long[]::new);
        if (distinctIds.length == 0) {
            return Set.of();
        }
        Long[] minorIds = findMinorPersonIds(distinctIds);
        if (minorIds.length == 0) {
            return Set.of();
        }
        return new HashSet<>(studentRepository.findPersonIdsWithEmailEqualToAnyGuardianEmail(minorIds));
    }

    private Long[] findMinorPersonIds(Long[] ids) {
        LocalDate today = ApplicationTime.today();
        LocalDate looseCutoff = today.minusYears(LegalAge.ADULT_YEARS).minusDays(1);
        return personRepository.findBirthDatesNewerThan(ids, looseCutoff).stream()
                .filter(row -> LegalAge.isMinor(row.getBirthDate(), today))
                .map(PersonBirthDateRow::getId)
                .toArray(Long[]::new);
    }
}
