package com.red_escuela.backend.billing.pricing.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface PriceVersionRepository extends JpaRepository<PriceVersion, Long> {

    Optional<PriceVersion> findFirstByPriceProgramIdAndPriceFeeTypeAndStatusInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescDecidedAtDescIdDesc(
            Long programId, FeeType feeType, Collection<PriceVersionStatus> statuses, LocalDate date);

    Optional<PriceVersion> findFirstByPriceCourseIdAndPriceFeeTypeAndStatusInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescDecidedAtDescIdDesc(
            Long courseId, FeeType feeType, Collection<PriceVersionStatus> statuses, LocalDate date);

    Optional<PriceVersion> findFirstByAuthorizationRequestId(Long authorizationRequestId);

    @Query("select new com.red_escuela.backend.billing.pricing.repository.PriceVersionCancellationState(v.price.id, v.status, v.effectiveFrom) from PriceVersion v where v.id = :id")
    Optional<PriceVersionCancellationState> findCancellationStateById(@Param("id") Long id);
}
