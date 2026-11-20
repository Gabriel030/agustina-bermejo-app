package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CertificateExceptionApprovalRepository extends JpaRepository<CertificateExceptionApproval, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CertificateExceptionApproval e where e.id = :id")
    Optional<CertificateExceptionApproval> findByIdForUpdate(@Param("id") Long id);

    List<CertificateExceptionApproval> findByStatusAndValidUntilLessThanEqual(CertificateExceptionStatus status, LocalDateTime moment);
}
