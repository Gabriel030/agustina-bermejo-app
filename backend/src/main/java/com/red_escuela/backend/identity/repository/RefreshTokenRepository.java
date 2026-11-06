package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByPersonAndRevokedFalseAndExpiresAtAfter(Person person, LocalDateTime now);

    List<RefreshToken> findByPerson(Person person);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true, rt.rotatedAt = :rotatedAt, rt.rotatedUserAgentHash = :userAgentHash "
            + "where rt.tokenHash = :tokenHash and rt.revoked = false")
    int rotateIfActive(@Param("tokenHash") String tokenHash, @Param("rotatedAt") LocalDateTime rotatedAt,
                       @Param("userAgentHash") String userAgentHash);

    @Modifying
    @Query("update RefreshToken rt set rt.graceUsed = true where rt.tokenHash = :tokenHash and rt.graceUsed = false")
    int markGraceUsedIfAvailable(@Param("tokenHash") String tokenHash);

    @Query("select rt.id as id, rt.person as person, rt.revoked as revoked, rt.expiresAt as expiresAt, rt.rotatedAt as rotatedAt, "
            + "rt.rotatedUserAgentHash as rotatedUserAgentHash, rt.graceUsed as graceUsed from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<RefreshTokenState> findStateByTokenHash(@Param("tokenHash") String tokenHash);
}
