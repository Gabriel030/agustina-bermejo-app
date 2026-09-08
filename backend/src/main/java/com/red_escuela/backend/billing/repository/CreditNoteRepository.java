package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    List<CreditNote> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    // sourceInvoices es lazy — para un caller que necesita leerla fuera de la transacción que
    // creó la nota (ej. un test), este variant la trae ya cargada en la misma query, mismo
    // criterio que InvoiceRepository.findByIdWithAdjustments.
    @Query("select cn from CreditNote cn left join fetch cn.sourceInvoices where cn.id = :id")
    Optional<CreditNote> findByIdWithSourceInvoices(@Param("id") Long id);

    // FIFO: las notas más viejas se consumen primero — mismo criterio de orden que
    // ScholarshipInstallment.consumeOne (por id ascendente).
    List<CreditNote> findByStudentIdAndStatusNotOrderByCreatedAtAsc(Long studentId, CreditNoteStatus status);

    // Solo ids, sin hidratar la entidad completa — CreditNoteService.consumeForInvoice depende de
    // que ninguna CreditNote entre al persistence context de esa transacción sin lock (ver el
    // comentario de "Corrección de auditoría 2026-09-07" ahí). Desempate por id además de
    // createdAt: dos notas creadas en el mismo instante (timestamp(6)) deben iterarse en un orden
    // determinístico entre transacciones concurrentes para no arriesgar deadlock.
    @Query("select cn.id from CreditNote cn where cn.student.id = :studentId and cn.status <> :status order by cn.createdAt asc, cn.id asc")
    List<Long> findIdsByStudentIdAndStatusNotOrderByCreatedAtAsc(@Param("studentId") Long studentId, @Param("status") CreditNoteStatus status);

    // Lock pesimista explícito, mismo criterio que Invoice.findByIdForUpdate/
    // PromotionRedemptionRepository.findByPaymentPlanSelectionIdForUpdate: dos pagos con
    // CREDIT_BALANCE concurrentes del mismo alumno podrían leer el mismo saldo remanente de una
    // nota antes de que ninguno commitee, gastando el mismo crédito dos veces.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cn from CreditNote cn where cn.id = :id")
    Optional<CreditNote> findByIdForUpdate(@Param("id") Long id);
}
