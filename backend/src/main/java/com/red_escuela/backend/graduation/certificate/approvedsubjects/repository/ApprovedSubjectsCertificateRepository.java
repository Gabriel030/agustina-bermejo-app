package com.red_escuela.backend.graduation.certificate.approvedsubjects.repository;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApprovedSubjectsCertificateRepository extends JpaRepository<ApprovedSubjectsCertificate, Long> {

    Optional<ApprovedSubjectsCertificate> findByCode(String code);

    List<ApprovedSubjectsCertificate> findByStudentIdOrderByIssuedAtDesc(Long studentId);

    Optional<ApprovedSubjectsCertificate> findByIdAndStudentId(Long id, Long studentId);

    boolean existsByCode(String code);

    long countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(Long studentId, LocalDateTime from, LocalDateTime until);

    Optional<ApprovedSubjectsCertificate> findFirstByProgramEnrollmentIdAndStatusOrderByIssuedAtDesc(
            Long programEnrollmentId, ApprovedSubjectsCertificateStatus status);

    List<ApprovedSubjectsCertificate> findByProgramEnrollmentIdAndStatus(Long programEnrollmentId, ApprovedSubjectsCertificateStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ApprovedSubjectsCertificate c where c.id = :id")
    Optional<ApprovedSubjectsCertificate> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update ApprovedSubjectsCertificate c set c.status = com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus.EXPIRED"
            + " where c.status = com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus.VALID and c.expiresAt <= :now")
    int expireAllUntil(@Param("now") LocalDateTime now);
}
