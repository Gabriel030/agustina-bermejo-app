package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends Repository<Refund, Long> {

    long countByStatusIn(Collection<RefundStatus> statuses);

    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    List<Refund> findByAuthorizationRequestIdOrderByIdAsc(Long authorizationRequestId);

    List<Refund> findByAuthorizationRequestIdAndStatusOrderByIdAsc(Long authorizationRequestId, RefundStatus status);

    List<Refund> findByRefundRequestIdOrderByIdAsc(Long refundRequestId);

    List<Refund> findByStudentCurrentAccountIdAndStatusIn(Long studentCurrentAccountId, Collection<RefundStatus> statuses);

    List<Refund> findByPaymentIdAndStatusIn(Long paymentId, Collection<RefundStatus> statuses);

    List<Refund> findByPaymentIdInAndStatusIn(Collection<Long> paymentIds, Collection<RefundStatus> statuses);

    List<Refund> findByStudentCurrentAccountIdInOrderByRequestedOnDescIdDesc(Collection<Long> studentCurrentAccountIds);

    @Query("select r.studentCurrentAccountId from Refund r where r.id = :refundId")
    Optional<Long> findStudentCurrentAccountIdById(@Param("refundId") Long refundId);

    @Query(value = "select nextval('refund_request_id_seq')", nativeQuery = true)
    Long nextRefundRequestId();
}
