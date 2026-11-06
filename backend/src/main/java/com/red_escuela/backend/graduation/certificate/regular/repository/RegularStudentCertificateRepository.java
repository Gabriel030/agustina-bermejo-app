package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegularStudentCertificateRepository extends JpaRepository<RegularStudentCertificate, Long> {

    Optional<RegularStudentCertificate> findByCode(String code);

    boolean existsByCode(String code);

    Optional<RegularStudentCertificate> findByProgramEnrollmentIdAndStatus(Long programEnrollmentId, RegularCertificateStatus status);

    Optional<RegularStudentCertificate> findByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, RegularCertificateStatus status);

    List<RegularStudentCertificate> findByStudentIdOrderByIssuedAtDesc(Long studentId);
}
