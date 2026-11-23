package com.red_escuela.backend.graduation.certificate.exam.repository;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamCertificateRepository extends JpaRepository<ExamCertificate, Long> {

    boolean existsByCode(String code);

    Optional<ExamCertificate> findByRegistrationIdAndStatus(Long registrationId, DocumentValidityStatus status);

    List<ExamCertificate> findByExamBoardIdAndRegistrationIdIn(Long examBoardId, Collection<Long> registrationIds);

    List<ExamCertificate> findByExamBoardIdOrderByIdDesc(Long examBoardId);
}
