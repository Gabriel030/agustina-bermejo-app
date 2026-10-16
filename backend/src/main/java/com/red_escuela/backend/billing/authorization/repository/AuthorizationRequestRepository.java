package com.red_escuela.backend.billing.authorization.repository;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AuthorizationRequestRepository extends JpaRepository<AuthorizationRequest, Long> {
    List<AuthorizationRequest> findByStatusAndExpiresOnLessThanEqual(AuthorizationRequestStatus status, LocalDate date);

    @Modifying
    @Query(value = "insert into authorization_request_invoice (authorization_request_id, invoice_id) values (:requestId, :invoiceId) on conflict do nothing", nativeQuery = true)
    void linkInvoiceToRequest(@Param("requestId") Long requestId, @Param("invoiceId") Long invoiceId);
}
