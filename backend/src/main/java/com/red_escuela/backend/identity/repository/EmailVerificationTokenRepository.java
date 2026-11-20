package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findByCancelTokenHash(String cancelTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t where t.cancelTokenHash = :cancelTokenHash")
    Optional<EmailVerificationToken> findLockedByCancelTokenHash(@Param("cancelTokenHash") String cancelTokenHash);

    boolean existsByPersonAndNewEmailIsNotNull(Person person);

    boolean existsByPersonAndNewEmailIsNotNullAndExpiresAtAfter(Person person, LocalDateTime now);

    boolean existsByNewEmailAndPersonNot(String newEmail, Person person);

    void deleteByPerson(Person person);
}
