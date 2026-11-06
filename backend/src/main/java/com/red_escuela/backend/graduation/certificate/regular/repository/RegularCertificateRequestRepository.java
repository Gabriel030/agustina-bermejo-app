package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegularCertificateRequestRepository extends JpaRepository<RegularCertificateRequest, Long> {

    List<RegularCertificateRequest> findByOutcomeOrderByRequestedAtDesc(RegularRequestOutcome outcome);

    List<RegularCertificateRequest> findByStudentIdOrderByRequestedAtDesc(Long studentId);

    List<RegularCertificateRequest> findTop200ByOrderByRequestedAtDesc();

    List<RegularCertificateRequest> findTop200ByOutcomeOrderByRequestedAtDesc(RegularRequestOutcome outcome);

    List<RegularCertificateRequest> findTop200ByStudentIdOrderByRequestedAtDesc(Long studentId);

    List<RegularCertificateRequest> findTop200ByStudentIdAndOutcomeOrderByRequestedAtDesc(Long studentId, RegularRequestOutcome outcome);
}
