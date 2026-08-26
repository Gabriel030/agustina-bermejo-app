package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CertificateIssuance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateIssuanceRepository extends JpaRepository<CertificateIssuance, Long> {

    List<CertificateIssuance> findByProgramEnrollmentId(Long programEnrollmentId);
}
