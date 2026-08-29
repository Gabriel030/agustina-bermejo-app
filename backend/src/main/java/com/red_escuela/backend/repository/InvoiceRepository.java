package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByStudentId(Long studentId);

    List<Invoice> findByStudentIdAndStatusIn(Long studentId, List<InvoiceStatus> statuses);

    // Idempotencia del motor de facturación recurrente (Épica 8 Fase 2): antes
    // de generar la cuota N, preguntar si ya existe — más barato y más claro
    // que dejar que el constraint único de la migración tire una excepción en
    // el camino feliz de un reintento.
    boolean existsByProgramEnrollmentIdAndInstallmentNumber(Long programEnrollmentId, Integer installmentNumber);

    boolean existsByCourseEnrollmentIdAndInstallmentNumber(Long courseEnrollmentId, Integer installmentNumber);
}
