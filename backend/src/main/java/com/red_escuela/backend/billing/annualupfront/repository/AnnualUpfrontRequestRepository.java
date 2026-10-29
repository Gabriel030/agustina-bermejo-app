package com.red_escuela.backend.billing.annualupfront.repository;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnualUpfrontRequestRepository extends JpaRepository<AnnualUpfrontRequest, Long> {

    Optional<AnnualUpfrontRequest> findByInvoiceId(Long invoiceId);
}
