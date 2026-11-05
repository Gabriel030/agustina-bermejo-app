package com.red_escuela.backend.billing.pricing.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.shared.domain.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    @EntityGraph(attributePaths = "versions")
    List<Price> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "versions")
    List<Price> findByProgramIdOrderByIdAsc(Long programId);

    @EntityGraph(attributePaths = "versions")
    List<Price> findByCourseIdOrderByIdAsc(Long courseId);

    Optional<Price> findByProgramIdAndFeeType(Long programId, FeeType feeType);

    Optional<Price> findByCourseIdAndFeeType(Long courseId, FeeType feeType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Price p where p.id = :id")
    Optional<Price> findByIdForUpdate(@Param("id") Long id);

    @Query("select p.currency from Price p where p.id = :id")
    Optional<Currency> findCurrencyById(@Param("id") Long id);

    @Query("select distinct p.id from Price p join p.versions v where v.status = com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus.SCHEDULED and v.effectiveFrom <= :today")
    List<Long> findIdsWithScheduledVersionsStartingOnOrBefore(@Param("today") LocalDate today);
}
