package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.LegalGuardianConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LegalGuardianConfirmationRepository extends JpaRepository<LegalGuardianConfirmation, Long> {

    @Modifying
    @Query("""
            update LegalGuardianConfirmation c set c.usedAt = :now
            where c.tokenHash = :tokenHash and c.usedAt is null and c.expiresAt > :now
            """)
    int claimByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    Optional<LegalGuardianConfirmation> findByTokenHash(String tokenHash);

    Optional<LegalGuardianConfirmation> findFirstByPersonIdOrderByCreatedAtDesc(Long personId);

    long countByPersonIdAndCreatedAtAfter(Long personId, LocalDateTime since);

    @Modifying
    @Query("""
            update LegalGuardianConfirmation c set c.expiresAt = :now
            where c.personId = :personId and c.usedAt is null and c.expiresAt > :now
            """)
    int expireOpenOnes(@Param("personId") Long personId, @Param("now") LocalDateTime now);
}
