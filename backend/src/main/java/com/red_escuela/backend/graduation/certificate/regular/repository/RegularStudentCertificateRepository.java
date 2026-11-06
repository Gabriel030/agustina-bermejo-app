package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegularStudentCertificateRepository extends JpaRepository<RegularStudentCertificate, Long> {

    Optional<RegularStudentCertificate> findByCode(String code);

    boolean existsByCode(String code);

    Optional<RegularStudentCertificate> findByProgramEnrollmentIdAndStatus(Long programEnrollmentId, RegularCertificateStatus status);

    Optional<RegularStudentCertificate> findByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, RegularCertificateStatus status);

    List<RegularStudentCertificate> findByStudentIdOrderByIssuedAtDesc(Long studentId);

    List<RegularStudentCertificate> findTop200ByOrderByIssuedAtDesc();

    List<RegularStudentCertificate> findTop200ByStatusOrderByIssuedAtDesc(RegularCertificateStatus status);

    List<RegularStudentCertificate> findTop200ByStudentIdOrderByIssuedAtDesc(Long studentId);

    List<RegularStudentCertificate> findTop200ByStudentIdAndStatusOrderByIssuedAtDesc(Long studentId, RegularCertificateStatus status);

    @Modifying
    @Query("update RegularStudentCertificate c set c.status = com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus.EXPIRED"
            + " where c.status = com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus.VALID and c.expiresAt < :now")
    int expireAllBefore(@Param("now") LocalDateTime now);
}
