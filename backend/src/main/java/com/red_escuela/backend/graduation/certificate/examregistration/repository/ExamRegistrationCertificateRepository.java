package com.red_escuela.backend.graduation.certificate.examregistration.repository;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExamRegistrationCertificateRepository extends JpaRepository<ExamRegistrationCertificate, Long> {

    List<ExamRegistrationCertificate> findByStudentIdOrderByIssuedAtDesc(Long studentId);

    boolean existsByCode(String code);

    Optional<ExamRegistrationCertificate> findByCode(String code);

    Optional<ExamRegistrationCertificate> findByIdAndStudentId(Long id, Long studentId);

    long countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(Long studentId, LocalDateTime from, LocalDateTime to);

    Optional<ExamRegistrationCertificate> findFirstByStudentIdAndRegistrationIdAndStatusOrderByIssuedAtDesc(
            Long studentId, Long registrationId, DocumentValidityStatus status);
}
