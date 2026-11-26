package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class GuardianEmailCollisionFinder {

    private final StudentRepository studentRepository;

    public GuardianEmailCollisionFinder(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Set<Long> findPersonIdsWithEmailSharedWithGuardian(Collection<Long> personIds) {
        Long[] distinctIds = personIds.stream().distinct().toArray(Long[]::new);
        if (distinctIds.length == 0) {
            return Set.of();
        }
        return new HashSet<>(studentRepository.findPersonIdsWithEmailEqualToAnyGuardianEmail(distinctIds));
    }
}
