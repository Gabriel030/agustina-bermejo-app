package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PromotionRedemption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {

    // Lock pesimista explícito: sin esto, dos invoices de la misma selección generadas
    // concurrentemente (el scheduler mensual y un disparo manual, por ejemplo) podrían leer el
    // mismo conteo de cuotas ya aplicadas antes de que la otra transacción comitee su propio save,
    // excediendo invoiceScopeCount — mismo problema estructural que ScholarshipRepository ya
    // resuelve con findActiveByCourseEnrollmentIdForUpdate/...ProgramEnrollmentIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PromotionRedemption r where r.paymentPlanSelection.id = :paymentPlanSelectionId")
    Optional<PromotionRedemption> findByPaymentPlanSelectionIdForUpdate(@Param("paymentPlanSelectionId") Long paymentPlanSelectionId);
}
