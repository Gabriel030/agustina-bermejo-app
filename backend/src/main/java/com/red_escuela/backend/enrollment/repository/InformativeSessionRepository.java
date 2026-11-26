package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InformativeSessionRepository extends JpaRepository<InformativeSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InformativeSession s where s.id = :id")
    Optional<InformativeSession> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from InformativeSession s where s.status = :status and s.scheduledAt >= :from order by s.scheduledAt asc, s.id asc")
    List<InformativeSession> findByStatusScheduledFrom(@Param("status") InformativeSessionStatus status, @Param("from") LocalDateTime from);
}
