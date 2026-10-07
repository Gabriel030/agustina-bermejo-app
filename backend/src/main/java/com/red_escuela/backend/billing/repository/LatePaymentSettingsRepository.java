package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface LatePaymentSettingsRepository extends JpaRepository<LatePaymentSettings, Long> {
    Optional<LatePaymentSettings> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate effectiveFrom);
}
