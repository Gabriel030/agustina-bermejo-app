package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payment_gateway_config")
public class PaymentGatewayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_alias", length = 50)
    private String bankAlias;

    @Column(name = "bank_cbu", length = 22)
    private String bankCbu;

    @Column(name = "account_holder", length = 100)
    private String accountHolder;

    @Column(name = "qr_image_path", length = 500)
    private String qrImagePath;

    @Column(name = "mp_enabled", nullable = false)
    private boolean mpEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Person updatedBy;
}
