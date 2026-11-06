package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.MpWebhookReceivedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MpWebhookReceivedEventRepository extends JpaRepository<MpWebhookReceivedEvent, Long> {

    @Modifying
    @Query(value = """
            insert into mp_webhook_received_event
                (received_at, x_signature, x_request_id, mp_payment_id, billing_mode_at_receipt, processing_status)
            values (:receivedAt, :xSignature, :xRequestId, :mpPaymentId, :billingMode, 'RECEIVED')
            on conflict (x_request_id) where x_request_id <> '' do nothing
            """, nativeQuery = true)
    int insertIfRequestIdIsNew(@Param("receivedAt") LocalDateTime receivedAt, @Param("xSignature") String xSignature,
                               @Param("xRequestId") String xRequestId, @Param("mpPaymentId") String mpPaymentId,
                               @Param("billingMode") String billingMode);
}
