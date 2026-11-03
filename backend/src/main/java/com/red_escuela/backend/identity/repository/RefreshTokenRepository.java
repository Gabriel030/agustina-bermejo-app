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
    @Query("update RefreshToken rt set rt.revoked = true where rt.tokenHash = :tokenHash and rt.revoked = false")
    int revokeIfActive(@Param("tokenHash") String tokenHash);
}
