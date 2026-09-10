package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
}
