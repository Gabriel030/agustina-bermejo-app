package com.red_escuela.backend.billing.annualupfront.repository;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnnualUpfrontRequestRepository extends JpaRepository<AnnualUpfrontRequest, Long> {

    Optional<AnnualUpfrontRequest> findByInvoiceId(Long invoiceId);

    @Query("select r.invoiceId from AnnualUpfrontRequest r where r.id = :id")
    Optional<Long> findInvoiceIdById(@Param("id") Long id);

    List<AnnualUpfrontRequest> findByRequestedByPersonIdOrderByIdDesc(Long requestedByPersonId);

    List<AnnualUpfrontRequest> findByProgramEnrollmentIdOrderByIdDesc(Long programEnrollmentId);

    @Query(value = "select year_number from term where id = :termId", nativeQuery = true)
    Optional<Integer> findLectiveYearByTermId(@Param("termId") Long termId);

    @Query(value = """
            select r.* from annual_upfront_request r join term t on t.id = r.lective_year_term_id
            where (cast(:status as varchar) is null or r.status = cast(:status as varchar))
              and (cast(:lectiveYear as integer) is null or t.year_number = cast(:lectiveYear as integer))
            order by r.id desc
            """, countQuery = """
            select count(*) from annual_upfront_request r join term t on t.id = r.lective_year_term_id
            where (cast(:status as varchar) is null or r.status = cast(:status as varchar))
              and (cast(:lectiveYear as integer) is null or t.year_number = cast(:lectiveYear as integer))
            """, nativeQuery = true)
    Page<AnnualUpfrontRequest> searchByStatusAndLectiveYear(@Param("status") String status, @Param("lectiveYear") Integer lectiveYear, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AnnualUpfrontRequest r where r.id = :id")
    Optional<AnnualUpfrontRequest> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select r.id from annual_upfront_request r join invoice i on i.id = r.invoice_id
            where r.status = 'AWAITING_PAYMENT' and r.payment_id is null and i.document_status = 'AWAITING_PAYMENT' and i.payment_deadline < :now
            order by r.id
            """, nativeQuery = true)
    List<Long> findIdsAwaitingPaymentWithDeadlineBefore(@Param("now") java.time.LocalDateTime now);

    @Query(value = "select r.program_enrollment_id from annual_upfront_request r where r.id = :id", nativeQuery = true)
    Optional<Long> findProgramEnrollmentIdById(@Param("id") Long id);

    @Query(value = """
            select count(*) > 0 from annual_upfront_request r
            where r.program_enrollment_id = :programEnrollmentId and r.status = 'CONFIRMED'
              and (select max(l.period) from invoice_line l where l.invoice_id = r.invoice_id) >= :currentMonthStart
            """, nativeQuery = true)
    boolean existsConfirmedWithUnfinishedLectiveYear(@Param("programEnrollmentId") Long programEnrollmentId, @Param("currentMonthStart") java.time.LocalDate currentMonthStart);
}
