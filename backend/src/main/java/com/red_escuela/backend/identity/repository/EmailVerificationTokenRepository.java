package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    boolean existsByPersonAndNewEmailIsNotNull(Person person);

    boolean existsByNewEmailAndPersonNot(String newEmail, Person person);

    void deleteByPerson(Person person);
}
