package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findFirstByPersonAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Person person, LocalDateTime now);

    Optional<PasswordResetToken> findByExchangeTokenHashAndExchangeExpiresAtAfter(
            String exchangeTokenHash, LocalDateTime now);
}
