package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payment_gateway_config_change")
public class PaymentGatewayConfigChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private Person changedBy;

    @Column(name = "mp_enabled_before", nullable = false)
    private boolean mpEnabledBefore;

    @Column(name = "mp_enabled_after", nullable = false)
    private boolean mpEnabledAfter;

    @Column(name = "bank_alias_before", length = 50)
    private String bankAliasBefore;

    @Column(name = "bank_alias_after", length = 50)
    private String bankAliasAfter;

    @Column(name = "bank_cbu_before", length = 22)
    private String bankCbuBefore;

    @Column(name = "bank_cbu_after", length = 22)
    private String bankCbuAfter;

    @Column(name = "account_holder_before", length = 100)
    private String accountHolderBefore;

    @Column(name = "account_holder_after", length = 100)
    private String accountHolderAfter;
}
