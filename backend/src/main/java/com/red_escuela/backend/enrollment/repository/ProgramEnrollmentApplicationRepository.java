package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
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

    @Query("""
            select count(a) > 0 from ProgramEnrollmentApplication a
            where a.student.id = :studentId and a.program.id = :programId
              and a.status = com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus.APPROVED
              and a.admissionInvoice.paymentStatus <> :paid
            """)
    boolean existsApprovedWithUnpaidAdmissionInvoice(@Param("studentId") Long studentId, @Param("programId") Long programId,
                                                     @Param("paid") InvoicePaymentStatus paid);
}
