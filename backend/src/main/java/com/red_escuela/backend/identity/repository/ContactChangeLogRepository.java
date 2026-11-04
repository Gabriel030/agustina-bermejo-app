package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;

public interface ContactChangeLogRepository extends JpaRepository<ContactChangeLog, Long> {

    boolean existsByPersonIdAndChangeTypeInAndOccurredAtAfter(Long personId, Collection<ContactChangeType> changeTypes,
                                                              LocalDateTime since);
}
