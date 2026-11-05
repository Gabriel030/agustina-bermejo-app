package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscountDefinitionRepository extends JpaRepository<DiscountDefinition, Long> {

    Optional<DiscountDefinition> findFirstByDiscountTypeAndStatusOrderByIdAsc(DiscountDefinitionType discountType, DiscountDefinitionStatus status);

    @Query("select d.status from DiscountDefinition d where d.id = :id")
    Optional<DiscountDefinitionStatus> findStatusById(@Param("id") Long id);

    @Query("select d from DiscountDefinition d where (:type is null or d.discountType = :type) and (:status is null or d.status = :status) order by d.id")
    List<DiscountDefinition> findCatalog(@Param("type") DiscountDefinitionType type, @Param("status") DiscountDefinitionStatus status);

    @Query("""
            select d.id from DiscountDefinition d
            where d.discountType = com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType.COHORT_PROMOTION
              and d.status = com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus.ACTIVE
              and d.programId = :programId and (d.programCohortId is null or d.programCohortId = :programCohortId)
            order by d.id""")
    List<Long> findActiveCohortPromotionIdsOfProgram(@Param("programId") Long programId, @Param("programCohortId") Long programCohortId);

    @Query("""
            select d.id from DiscountDefinition d
            where d.discountType = com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType.COHORT_PROMOTION
              and d.status = com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus.ACTIVE
              and d.courseId = :courseId
            order by d.id""")
    List<Long> findActiveCohortPromotionIdsOfCourse(@Param("courseId") Long courseId);

    @Query("""
            select d from DiscountDefinition d
            where d.discountType = com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType.ANNUAL_UPFRONT
              and d.status = com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus.ACTIVE
            order by d.id""")
    List<DiscountDefinition> findActiveAnnualUpfrontDefinitions();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiscountDefinition d where d.id = :id")
    Optional<DiscountDefinition> findByIdForUpdate(@Param("id") Long id);
}
