package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentStanding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentPaymentStandingRepository extends JpaRepository<EnrollmentPaymentStanding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EnrollmentPaymentStanding s where s.programEnrollmentId = :enrollmentId")
    Optional<EnrollmentPaymentStanding> findByProgramEnrollmentForUpdate(@Param("enrollmentId") Long enrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EnrollmentPaymentStanding s where s.courseEnrollmentId = :enrollmentId")
    Optional<EnrollmentPaymentStanding> findByCourseEnrollmentForUpdate(@Param("enrollmentId") Long enrollmentId);

    @Query("select s from EnrollmentPaymentStanding s where s.programEnrollmentId = :enrollmentId")
    Optional<EnrollmentPaymentStanding> findByProgramEnrollment(@Param("enrollmentId") Long enrollmentId);

    @Query("select s from EnrollmentPaymentStanding s where s.courseEnrollmentId = :enrollmentId")
    Optional<EnrollmentPaymentStanding> findByCourseEnrollment(@Param("enrollmentId") Long enrollmentId);

    @Query("select s.programEnrollmentId from EnrollmentPaymentStanding s where s.programEnrollmentId is not null and s.status = com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.SUSPENDED")
    List<Long> findSuspendedProgramEnrollmentIds();

    @Query("select s from EnrollmentPaymentStanding s where s.programEnrollmentId in :ids")
    List<EnrollmentPaymentStanding> findAllByProgramEnrollmentIds(@Param("ids") java.util.Collection<Long> ids);

    @Query("select s from EnrollmentPaymentStanding s where s.courseEnrollmentId in :ids")
    List<EnrollmentPaymentStanding> findAllByCourseEnrollmentIds(@Param("ids") java.util.Collection<Long> ids);
}
