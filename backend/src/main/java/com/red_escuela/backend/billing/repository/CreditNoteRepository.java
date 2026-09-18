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

    @Query("select cn from CreditNote cn left join fetch cn.sourceInvoices where cn.id = :id")
    Optional<CreditNote> findByIdWithSourceInvoices(@Param("id") Long id);

    List<CreditNote> findByStudentIdAndStatusNotOrderByCreatedAtAsc(Long studentId, CreditNoteStatus status);

    @Query("select cn.id from CreditNote cn where cn.student.id = :studentId and cn.status <> :status order by cn.createdAt asc, cn.id asc")
    List<Long> findIdsByStudentIdAndStatusNotOrderByCreatedAtAsc(@Param("studentId") Long studentId, @Param("status") CreditNoteStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cn from CreditNote cn where cn.id = :id")
    Optional<CreditNote> findByIdForUpdate(@Param("id") Long id);
}
