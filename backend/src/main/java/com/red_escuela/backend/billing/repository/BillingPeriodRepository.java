package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.BillingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {

    Optional<BillingPeriod> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate date);
}
