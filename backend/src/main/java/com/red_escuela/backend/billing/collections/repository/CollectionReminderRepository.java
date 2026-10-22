package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.CollectionReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CollectionReminderRepository extends JpaRepository<CollectionReminder, Long> {

    boolean existsByStudentIdAndBusinessDate(Long studentId, LocalDate businessDate);

    Optional<CollectionReminder> findByStudentIdAndBusinessDate(Long studentId, LocalDate businessDate);
}
