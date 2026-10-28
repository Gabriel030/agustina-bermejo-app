package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.DiscountEnrollmentIds;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountOrigin;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentDiscountRepository extends JpaRepository<StudentDiscount, Long>, JpaSpecificationExecutor<StudentDiscount> {

    @Query("select d.status from StudentDiscount d where d.id = :id")
    Optional<StudentDiscountStatus> findStatusById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StudentDiscount d where d.id = :id")
    Optional<StudentDiscount> findByIdForUpdate(@Param("id") Long id);

    List<StudentDiscount> findByProgramEnrollmentIdAndStatus(Long programEnrollmentId, StudentDiscountStatus status);

    List<StudentDiscount> findByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, StudentDiscountStatus status);

    long countByDiscountDefinitionId(Long discountDefinitionId);

    long countByDiscountDefinitionIdAndOrigin(Long discountDefinitionId, StudentDiscountOrigin origin);

    Optional<StudentDiscount> findFirstByProgramEnrollmentIdAndOriginOrderByIdAsc(Long programEnrollmentId, StudentDiscountOrigin origin);

    Optional<StudentDiscount> findFirstByCourseEnrollmentIdAndOriginOrderByIdAsc(Long courseEnrollmentId, StudentDiscountOrigin origin);

    List<StudentDiscount> findByOriginAndStatusInOrderByIdAsc(StudentDiscountOrigin origin, Collection<StudentDiscountStatus> statuses);

    List<StudentDiscount> findByStudentIdOrderByIdDesc(Long studentId);

    List<StudentDiscount> findByProgramEnrollmentIdOrderByIdDesc(Long programEnrollmentId);

    List<StudentDiscount> findByCourseEnrollmentIdOrderByIdDesc(Long courseEnrollmentId);

    List<StudentDiscount> findByProgramEnrollmentIdAndStatusInOrderByIdAsc(Long programEnrollmentId, Collection<StudentDiscountStatus> statuses);

    List<StudentDiscount> findByCourseEnrollmentIdAndStatusInOrderByIdAsc(Long courseEnrollmentId, Collection<StudentDiscountStatus> statuses);

    @Query("""
            select distinct new com.red_escuela.backend.billing.discount.domain.DiscountEnrollmentIds(s.programEnrollmentId, s.courseEnrollmentId)
            from StudentDiscount s
            where s.id in (select d.studentDiscountId from InvoiceDiscount d join d.invoiceLine l where l.invoice.id = :invoiceId and d.consumptionReleasedAt is null)
            """)
    List<DiscountEnrollmentIds> findEnrollmentsOfDiscountsLiveOnInvoice(@Param("invoiceId") Long invoiceId);

    Optional<StudentDiscount> findFirstByProgramEnrollmentIdAndOriginAndStatusAndFromPeriodOrderByIdAsc(Long programEnrollmentId, StudentDiscountOrigin origin,
                                                                                                         StudentDiscountStatus status, YearMonth fromPeriod);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StudentDiscount d where d.programEnrollmentId = :enrollmentId and d.status in :statuses order by d.id")
    List<StudentDiscount> findByProgramEnrollmentIdAndStatusInForUpdate(@Param("enrollmentId") Long enrollmentId, @Param("statuses") Collection<StudentDiscountStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StudentDiscount d where d.courseEnrollmentId = :enrollmentId and d.status in :statuses order by d.id")
    List<StudentDiscount> findByCourseEnrollmentIdAndStatusInForUpdate(@Param("enrollmentId") Long enrollmentId, @Param("statuses") Collection<StudentDiscountStatus> statuses);
}
