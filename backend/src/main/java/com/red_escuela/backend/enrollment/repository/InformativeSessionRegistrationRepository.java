package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.InformativeSessionRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InformativeSessionRegistrationRepository extends JpaRepository<InformativeSessionRegistration, Long> {

    long countBySessionId(Long sessionId);

    boolean existsBySessionIdAndPersonId(Long sessionId, Long personId);

    @Query("select r.sessionId as sessionId, count(r) as total from InformativeSessionRegistration r where r.sessionId in :sessionIds group by r.sessionId")
    List<RegistrationTotal> countBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    @Query("select r.sessionId from InformativeSessionRegistration r where r.personId = :personId and r.sessionId in :sessionIds")
    List<Long> findSessionIdsRegisteredBy(@Param("personId") Long personId, @Param("sessionIds") Collection<Long> sessionIds);

    interface RegistrationTotal {
        Long getSessionId();

        Long getTotal();
    }
}
