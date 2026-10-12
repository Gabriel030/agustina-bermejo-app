package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProgramEnrollmentApplicationRepository extends JpaRepository<ProgramEnrollmentApplication, Long> {

    List<ProgramEnrollmentApplication> findByStudentIdOrderByRequestedAtDesc(Long studentId);

    List<ProgramEnrollmentApplication> findByStatusOrderByRequestedAtAsc(ProgramEnrollmentApplicationStatus status);

    Optional<ProgramEnrollmentApplication> findByStudentIdAndProgramIdAndStatus(
            Long studentId, Long programId, ProgramEnrollmentApplicationStatus status);

    Optional<ProgramEnrollmentApplication> findByAdmissionInvoiceIdAndStatus(Long admissionInvoiceId, ProgramEnrollmentApplicationStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ProgramEnrollmentApplication a where a.admissionInvoice.id = :invoiceId and a.status = :status")
    Optional<ProgramEnrollmentApplication> findLockedByAdmissionInvoiceIdAndStatus(@Param("invoiceId") Long admissionInvoiceId,
                                                                                 @Param("status") ProgramEnrollmentApplicationStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ProgramEnrollmentApplication a where a.id = :id")
    Optional<ProgramEnrollmentApplication> findLockedById(@Param("id") Long id);

    @Query("""
            select count(a) > 0 from ProgramEnrollmentApplication a
            where a.student.id = :studentId and a.program.id = :programId
              and a.status = com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus.APPROVED
              and a.admissionInvoice.paymentStatus <> com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.PAID
            """)
    boolean existsApprovedWithUnpaidAdmissionInvoice(@Param("studentId") Long studentId, @Param("programId") Long programId);
}
