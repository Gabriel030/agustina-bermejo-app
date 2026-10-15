package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends Repository<Payment, Long> {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    List<Payment> findByIdIn(Collection<Long> ids);

    List<Payment> findByStudentCurrentAccountIdInOrderByRegisteredAtDescIdDesc(Collection<Long> studentCurrentAccountIds);

    boolean existsByGatewayAndGatewayPaymentId(PaymentGatewayCode gateway, String gatewayPaymentId);

    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:reference))) as acquired", nativeQuery = true)
    long lockGatewayReferenceUntilTransactionEnds(@Param("reference") String reference);
}
