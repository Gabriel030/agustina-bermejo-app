package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentDiscountRepository extends JpaRepository<StudentDiscount, Long> {

    @Query("select d.status from StudentDiscount d where d.id = :id")
    Optional<StudentDiscountStatus> findStatusById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StudentDiscount d where d.id = :id")
    Optional<StudentDiscount> findByIdForUpdate(@Param("id") Long id);

    List<StudentDiscount> findByProgramEnrollmentIdAndStatus(Long programEnrollmentId, StudentDiscountStatus status);

    List<StudentDiscount> findByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, StudentDiscountStatus status);

    List<StudentDiscount> findByProgramEnrollmentIdOrderByIdDesc(Long programEnrollmentId);

    List<StudentDiscount> findByCourseEnrollmentIdOrderByIdDesc(Long courseEnrollmentId);
}
