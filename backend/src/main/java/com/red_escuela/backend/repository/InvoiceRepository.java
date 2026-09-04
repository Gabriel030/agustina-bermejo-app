package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Completed with findByStatusAndDueDateBefore / pessimistic lock in Fase 6.2/6.3.
}
