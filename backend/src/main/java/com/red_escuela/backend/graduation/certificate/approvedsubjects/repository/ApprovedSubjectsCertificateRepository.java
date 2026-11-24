package com.red_escuela.backend.graduation.certificate.approvedsubjects.repository;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovedSubjectsCertificateRepository extends JpaRepository<ApprovedSubjectsCertificate, Long> {

    Optional<ApprovedSubjectsCertificate> findByCode(String code);

    List<ApprovedSubjectsCertificate> findByStudentIdOrderByIssuedAtDesc(Long studentId);
}
