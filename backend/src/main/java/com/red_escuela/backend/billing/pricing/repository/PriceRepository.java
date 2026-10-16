package com.red_escuela.backend.billing.pricing.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findByProgramIdAndFeeType(Long programId, FeeType feeType);

    Optional<Price> findByCourseIdAndFeeType(Long courseId, FeeType feeType);

    @Query("select distinct p from Price p join p.versions v where v.status = com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus.SCHEDULED and v.effectiveFrom <= :today")
    List<Price> findWithScheduledVersionsStartingOnOrBefore(@Param("today") LocalDate today);
}
