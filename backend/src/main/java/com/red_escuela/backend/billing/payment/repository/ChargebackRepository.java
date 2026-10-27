package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.Chargeback;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargebackRepository extends Repository<Chargeback, Long> {

    Chargeback save(Chargeback chargeback);

    Optional<Chargeback> findById(Long id);

    List<Chargeback> findByPaymentIdOrderByIdAsc(Long paymentId);

    List<Chargeback> findByPaymentIdInOrderByIdAsc(Collection<Long> paymentIds);

    @Query("select c.paymentId from Chargeback c where c.id = :chargebackId")
    Optional<Long> findPaymentIdById(@Param("chargebackId") Long chargebackId);
}
