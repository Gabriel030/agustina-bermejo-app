package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByCourseEnrollmentIdOrderByPeriod(Long courseEnrollmentId);

    List<Invoice> findByProgramEnrollmentIdOrderByPeriod(Long programEnrollmentId);

    // Matrícula: nace una sola vez por ProgramEnrollment (ver
    // ProgramEnrollment.canEnrollInSubjects) — a lo sumo una fila.
    Optional<Invoice> findByProgramEnrollmentIdAndFeeType(Long programEnrollmentId, FeeType feeType);

    // adjustments es lazy — para un caller que necesita leerlas fuera de la transacción que hizo
    // la consulta (ej. un test), este variant las trae ya cargadas en la misma query.
    @Query("select distinct i from Invoice i left join fetch i.adjustments where i.programEnrollment.id = :programEnrollmentId order by i.period")
    List<Invoice> findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(@Param("programEnrollmentId") Long programEnrollmentId);

    @Query("select i from Invoice i left join fetch i.adjustments where i.id = :id")
    Optional<Invoice> findByIdWithAdjustments(@Param("id") Long id);

    // Lock pesimista explícito (SELECT ... FOR UPDATE) — @Version protege el caso de dos pagos
    // que cubren el saldo COMPLETO en paralelo, pero no dos pagos PARCIALES simultáneos que
    // individualmente no superan el saldo pero sumados sí (PaymentService.registrarPago).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    // InvoiceOverdueSweepService (Fase 6.3): keyset pagination (id > :lastId), no OFFSET — paginar
    // con OFFSET sobre un WHERE status=PENDING mientras la misma corrida muta ese status a OVERDUE
    // pierde filas sin error visible (ver nota de la spec).
    List<Invoice> findByStatusAndDueDateBeforeAndIdGreaterThanOrderByIdAsc(
            InvoiceStatus status, LocalDate dueDate, Long lastId, Pageable pageable);

    Optional<Invoice> findFirstByCourseEnrollmentIdAndStatusInOrderByDueDateAsc(Long courseEnrollmentId, List<InvoiceStatus> statuses);

    Optional<Invoice> findFirstByProgramEnrollmentIdAndStatusInOrderByDueDateAsc(Long programEnrollmentId, List<InvoiceStatus> statuses);

    long countByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, InvoiceStatus status);

    long countByProgramEnrollmentIdAndStatus(Long programEnrollmentId, InvoiceStatus status);

    @Query("""
            select case when count(i) > 0 then true else false end from Invoice i
            left join i.courseEnrollment ce
            left join i.programEnrollment pe
            where i.status = :status
              and (ce.student.id = :studentId or pe.student.id = :studentId)
            """)
    boolean existsByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") InvoiceStatus status);

    List<Invoice> findByCourseEnrollmentIdAndPeriodBetween(Long courseEnrollmentId, YearMonth from, YearMonth to);

    List<Invoice> findByProgramEnrollmentIdAndPeriodBetween(Long programEnrollmentId, YearMonth from, YearMonth to);

    List<Invoice> findByCourseEnrollmentIdAndStatusIn(Long courseEnrollmentId, List<InvoiceStatus> statuses);

    List<Invoice> findByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<InvoiceStatus> statuses);
}
