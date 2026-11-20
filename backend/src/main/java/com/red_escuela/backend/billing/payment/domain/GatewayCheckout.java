package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gateway_checkout")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class GatewayCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "public_reference", nullable = false, updatable = false)
    private UUID publicReference;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, updatable = false, length = 20)
    private PaymentGatewayCode gateway;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "currency", nullable = false, updatable = false, length = 3))})
    private NonNegativeMoney amount;

    @Column(name = "gateway_preference_id", nullable = false, updatable = false, length = 120)
    private String gatewayPreferenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;
}
