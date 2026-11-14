package com.red_escuela.backend.billing.pricing.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceVersionRepository extends JpaRepository<PriceVersion, Long> {

    Optional<PriceVersion> findFirstByPriceProgramIdAndPriceFeeTypeAndStatusInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescDecidedAtDescIdDesc(
            Long programId, FeeType feeType, Collection<PriceVersionStatus> statuses, LocalDate date);

    Optional<PriceVersion> findFirstByPriceCourseIdAndPriceFeeTypeAndStatusInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescDecidedAtDescIdDesc(
            Long courseId, FeeType feeType, Collection<PriceVersionStatus> statuses, LocalDate date);

    @Query("select v from PriceVersion v join fetch v.price p where p.programId in :programIds and p.feeType = :feeType and v.status in :statuses and v.effectiveFrom <= :date")
    List<PriceVersion> findResolvableByProgramIds(@Param("programIds") Collection<Long> programIds, @Param("feeType") FeeType feeType,
                                                  @Param("statuses") Collection<PriceVersionStatus> statuses, @Param("date") LocalDate date);

    @Query("select v from PriceVersion v join fetch v.price p where p.courseId in :courseIds and p.feeType = :feeType and v.status in :statuses and v.effectiveFrom <= :date")
    List<PriceVersion> findResolvableByCourseIds(@Param("courseIds") Collection<Long> courseIds, @Param("feeType") FeeType feeType,
                                                 @Param("statuses") Collection<PriceVersionStatus> statuses, @Param("date") LocalDate date);

    Optional<PriceVersion> findFirstByAuthorizationRequestId(Long authorizationRequestId);

    List<PriceVersion> findByPriceProgramIdAndPriceFeeTypeAndAppliesToLectiveYearAndStatusIn(
            Long programId, FeeType feeType, Integer lectiveYear, Collection<PriceVersionStatus> statuses);

    @Query("select new com.red_escuela.backend.billing.pricing.repository.PriceVersionCancellationState(v.price.id, v.status, v.effectiveFrom) from PriceVersion v where v.id = :id")
    Optional<PriceVersionCancellationState> findCancellationStateById(@Param("id") Long id);
}
