package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.PersonBirthDateRow;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MinorGuardianEmailCollisionFinder {

    private static final int LOOKUP_CHUNK_SIZE = 500;

    private final StudentRepository studentRepository;
    private final MinorAgeChecker minorAgeChecker;

    public MinorGuardianEmailCollisionFinder(StudentRepository studentRepository, MinorAgeChecker minorAgeChecker) {
        this.studentRepository = studentRepository;
        this.minorAgeChecker = minorAgeChecker;
    }

    @Transactional(readOnly = true)
    public Set<Long> findMinorPersonIdsWithEmailSharedWithGuardian(Collection<Long> personIds) {
        Set<Long> minors = new HashSet<>();
        List<Long> ids = personIds.stream().distinct().toList();
        for (int from = 0; from < ids.size(); from += LOOKUP_CHUNK_SIZE) {
            List<Long> chunk = ids.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, ids.size()));
            for (PersonBirthDateRow row : studentRepository.findBirthDatesOfPeopleWithEmailEqualToAnyGuardianEmail(chunk)) {
                if (minorAgeChecker.isMinor(row.getBirthDate())) {
                    minors.add(row.getId());
                }
            }
        }
        return minors;
    }
}
