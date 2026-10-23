package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends Repository<Payment, Long> {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    @Query("select p.studentCurrentAccountId from Payment p where p.id = :paymentId")
    Optional<Long> findStudentCurrentAccountIdById(@Param("paymentId") Long paymentId);

    List<Payment> findByIdIn(Collection<Long> ids);

    List<Payment> findByStudentCurrentAccountIdInOrderByRegisteredAtDescIdDesc(Collection<Long> studentCurrentAccountIds);

    List<Payment> findByStudentCurrentAccountIdAndGatewayAndStatus(Long studentCurrentAccountId, PaymentGatewayCode gateway, PaymentStatus status);

    boolean existsByGatewayAndGatewayPaymentId(PaymentGatewayCode gateway, String gatewayPaymentId);

    @Query(value = """
            select exists (select 1 from payment
                           where method_type = 'BANK_TRANSFER' and status not in ('REJECTED', 'CANCELLED')
                             and method_details ->> 'operationNumber' = :operationNumber
                             and method_details ->> 'transferDate' = :transferDate
                             and amount = :amount)
            """, nativeQuery = true)
    boolean existsActiveBankTransfer(@Param("operationNumber") String operationNumber, @Param("transferDate") String transferDate,
                                     @Param("amount") java.math.BigDecimal amount);

    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:reference))) as acquired", nativeQuery = true)
    long lockGatewayPaymentUntilTransactionEnds(@Param("reference") String reference);
}
