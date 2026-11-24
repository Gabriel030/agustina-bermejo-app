package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GuardianEmailCollisionFinder {

    private static final int LOOKUP_CHUNK_SIZE = 500;

    private final StudentRepository studentRepository;

    public GuardianEmailCollisionFinder(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Set<Long> findPersonIdsWithEmailSharedWithGuardian(Collection<Long> personIds) {
        Set<Long> colliding = new HashSet<>();
        List<Long> ids = personIds.stream().distinct().toList();
        for (int from = 0; from < ids.size(); from += LOOKUP_CHUNK_SIZE) {
            colliding.addAll(studentRepository.findPersonIdsWithEmailEqualToAnyGuardianEmail(ids.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, ids.size()))));
        }
        return colliding;
    }
}
