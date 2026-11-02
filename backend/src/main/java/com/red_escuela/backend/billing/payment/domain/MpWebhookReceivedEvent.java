package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.module.BillingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mp_webhook_received_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MpWebhookReceivedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "x_signature", nullable = false, updatable = false, length = 500)
    private String xSignature;

    @Column(name = "x_request_id", nullable = false, updatable = false, length = 200)
    private String xRequestId;

    @Column(name = "mp_payment_id", nullable = false, updatable = false, length = 100)
    private String mpPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode_at_receipt", nullable = false, updatable = false, length = 10)
    private BillingMode billingModeAtReceipt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private MpWebhookProcessingStatus processingStatus;

    public static MpWebhookReceivedEvent receivedAt(LocalDateTime receivedAt, String xSignature, String xRequestId,
                                                    String mpPaymentId, BillingMode billingModeAtReceipt) {
        MpWebhookReceivedEvent event = new MpWebhookReceivedEvent();
        event.receivedAt = receivedAt;
        event.xSignature = abbreviated(xSignature, 500);
        event.xRequestId = abbreviated(xRequestId, 200);
        event.mpPaymentId = abbreviated(mpPaymentId, 100);
        event.billingModeAtReceipt = billingModeAtReceipt;
        event.processingStatus = MpWebhookProcessingStatus.RECEIVED;
        return event;
    }

    private static String abbreviated(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
