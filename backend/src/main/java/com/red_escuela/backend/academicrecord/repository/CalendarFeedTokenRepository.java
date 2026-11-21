package com.red_escuela.backend.academicrecord.repository;

import com.red_escuela.backend.academicrecord.entity.CalendarFeedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CalendarFeedTokenRepository extends JpaRepository<CalendarFeedToken, Long> {

    Optional<CalendarFeedToken> findByStudentIdAndRevokedAtIsNull(Long studentId);

    Optional<CalendarFeedToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Transactional
    @Modifying
    @Query("update CalendarFeedToken t set t.lastUsedAt = :now where t.id = :id "
            + "and (t.lastUsedAt is null or t.lastUsedAt < :staleBefore)")
    int touchLastUsedAt(@Param("id") Long id, @Param("now") LocalDateTime now,
                        @Param("staleBefore") LocalDateTime staleBefore);

    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, cast(:studentId as integer))) as lock",
            nativeQuery = true)
    int lockStudentFeedTokens(@Param("namespace") int namespace, @Param("studentId") Long studentId);
}
