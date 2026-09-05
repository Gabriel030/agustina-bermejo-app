package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Scholarship;
import com.red_escuela.backend.entity.ScholarshipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {

    List<Scholarship> findByCourseEnrollmentId(Long courseEnrollmentId);

    List<Scholarship> findByProgramEnrollmentId(Long programEnrollmentId);

    Optional<Scholarship> findByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, ScholarshipStatus status);

    Optional<Scholarship> findByProgramEnrollmentIdAndStatus(Long programEnrollmentId, ScholarshipStatus status);

    @Query("select s from Scholarship s left join fetch s.installments where s.id = :id")
    Optional<Scholarship> findByIdWithInstallments(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Scholarship s where s.courseEnrollment.id = :courseEnrollmentId and s.status = com.red_escuela.backend.entity.ScholarshipStatus.ACTIVE")
    Optional<Scholarship> findActiveByCourseEnrollmentIdForUpdate(@Param("courseEnrollmentId") Long courseEnrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Scholarship s where s.programEnrollment.id = :programEnrollmentId and s.status = com.red_escuela.backend.entity.ScholarshipStatus.ACTIVE")
    Optional<Scholarship> findActiveByProgramEnrollmentIdForUpdate(@Param("programEnrollmentId") Long programEnrollmentId);
}
