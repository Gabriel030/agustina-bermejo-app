package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "discount_definition_status_change")
public class DiscountDefinitionStatusChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "discount_definition_id", nullable = false)
    private DiscountDefinition discountDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private DiscountDefinitionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private DiscountDefinitionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false)
    private DiscountDefinitionEvent event;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "actor_process")
    private String actorProcess;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason")
    private String reason;

    DiscountDefinitionStatusChange(DiscountDefinition discountDefinition, DiscountDefinitionStatus fromStatus, DiscountDefinitionStatus toStatus,
                                   DiscountDefinitionEvent event, StatusChangeActor actor, Instant changedAt, String reason) {
        this.discountDefinition = discountDefinition;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.event = event;
        this.actorPersonId = actor.personId();
        this.actorProcess = actor.processName();
        this.changedAt = changedAt;
        this.reason = reason;
    }
}
