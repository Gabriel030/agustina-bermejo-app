package com.red_escuela.backend.graduation.certificate.repository;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GraduationCertificateRepository extends JpaRepository<GraduationCertificate, Long> {

    Optional<GraduationCertificate> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByProgramEnrollmentIdAndStatus(Long programEnrollmentId, GraduationCertificateStatus status);

    boolean existsByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, GraduationCertificateStatus status);
}
