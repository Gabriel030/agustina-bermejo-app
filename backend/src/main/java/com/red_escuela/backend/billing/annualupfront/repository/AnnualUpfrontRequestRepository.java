package com.red_escuela.backend.billing.annualupfront.repository;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnnualUpfrontRequestRepository extends JpaRepository<AnnualUpfrontRequest, Long> {

    Optional<AnnualUpfrontRequest> findByInvoiceId(Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AnnualUpfrontRequest r where r.id = :id")
    Optional<AnnualUpfrontRequest> findByIdForUpdate(@Param("id") Long id);
}
