package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PromotionRedemption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PromotionRedemption r where r.paymentPlanSelection.id = :paymentPlanSelectionId")
    Optional<PromotionRedemption> findByPaymentPlanSelectionIdForUpdate(@Param("paymentPlanSelectionId") Long paymentPlanSelectionId);

    @Query("""
            select distinct r from PromotionRedemption r
            join fetch r.promotion p
            join fetch r.paymentPlanSelection s
            left join fetch p.benefits
            left join fetch s.courseEnrollment ce left join fetch ce.student cs left join fetch cs.person
            left join fetch s.programEnrollment pe left join fetch pe.student ps left join fetch ps.person
            where s.effectiveTo is not null
            order by r.id
            """)
    List<PromotionRedemption> findWithClosedPlanSelectionAndBenefits();
}
