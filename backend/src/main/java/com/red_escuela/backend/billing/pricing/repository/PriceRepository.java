package com.red_escuela.backend.billing.pricing.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findByProgramIdAndFeeType(Long programId, FeeType feeType);

    Optional<Price> findByCourseIdAndFeeType(Long courseId, FeeType feeType);
}
