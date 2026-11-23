package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.ActivationToken;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    Optional<ActivationToken> findByTokenHash(String tokenHash);

    void deleteByPerson(Person person);

    @Modifying(flushAutomatically = true)
    @Query("update ActivationToken t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now")
    int markAsUsedIfRedeemable(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
