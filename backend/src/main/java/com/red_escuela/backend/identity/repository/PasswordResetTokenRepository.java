package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findFirstByPersonAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Person person, LocalDateTime now);

    Optional<PasswordResetToken> findByExchangeTokenHashAndExchangeExpiresAtAfter(
            String exchangeTokenHash, LocalDateTime now);

    @Query(value = """
            insert into password_reset_failure (person_id, source_hash, failures, window_start)
            values (:personId, :sourceHash, 1, :now)
            on conflict (person_id, source_hash) do update set
                failures = case when password_reset_failure.window_start <= :expiredBefore then 1
                                else password_reset_failure.failures + 1 end,
                window_start = case when password_reset_failure.window_start <= :expiredBefore then :now
                                    else password_reset_failure.window_start end
            returning failures
            """, nativeQuery = true)
    int registerFailure(@Param("personId") Long personId, @Param("sourceHash") String sourceHash,
                        @Param("now") LocalDateTime now, @Param("expiredBefore") LocalDateTime expiredBefore);

    @Query(value = """
            select count(*) from password_reset_failure
            where person_id = :personId and source_hash = :sourceHash
              and failures >= :maxFailures and window_start > :expiredBefore
            """, nativeQuery = true)
    long countBlockingFailures(@Param("personId") Long personId, @Param("sourceHash") String sourceHash,
                               @Param("maxFailures") int maxFailures, @Param("expiredBefore") LocalDateTime expiredBefore);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from password_reset_failure where person_id = :personId and source_hash = :sourceHash", nativeQuery = true)
    int clearFailures(@Param("personId") Long personId, @Param("sourceHash") String sourceHash);

    @Modifying(flushAutomatically = true)
    @Query(value = "update password_reset_token set used = true where person_id = :personId and used = false", nativeQuery = true)
    int invalidateActiveCodes(@Param("personId") Long personId);
}
