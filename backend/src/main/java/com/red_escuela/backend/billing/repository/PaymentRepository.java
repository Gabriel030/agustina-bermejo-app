package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository("legacyPaymentRepository")
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByExternalReference(String externalReference);

    @org.springframework.data.jpa.repository.Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:reference))) as acquired", nativeQuery = true)
    long lockExternalReferenceUntilTransactionEnds(@org.springframework.data.repository.query.Param("reference") String reference);
}
