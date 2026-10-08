package com.red_escuela.backend.billing.configuration.domain;

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
@Table(name = "configuration_version_status_change")
public class ConfigurationVersionStatusChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "configuration_version_id", nullable = false)
    private ConfigurationVersion configurationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ConfigurationVersionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ConfigurationVersionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false)
    private ConfigurationVersionEvent event;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "actor_process")
    private String actorProcess;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason")
    private String reason;

    ConfigurationVersionStatusChange(ConfigurationVersion configurationVersion, ConfigurationVersionStatus fromStatus, ConfigurationVersionStatus toStatus,
                                     ConfigurationVersionEvent event, StatusChangeActor actor, Instant changedAt, String reason) {
        this.configurationVersion = configurationVersion;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.event = event;
        this.actorPersonId = actor.personId();
        this.actorProcess = actor.processName();
        this.changedAt = changedAt;
        this.reason = reason;
    }
}
