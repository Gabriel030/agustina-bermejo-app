package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "configuration_version")
public class ConfigurationVersion {
    private static final String ACTIVATION_PROCESS = "ConfigurationVersionActivation";
    private static final String EXPIRATION_PROCESS = "ConfigurationProposalExpiration";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private ConfigurationScope scope;

    @Column(name = "topic_key", nullable = false)
    private String topicKey;

    @Column(name = "program_id")
    private Long programId;

    @Column(name = "term_id")
    private Long termId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "cohort_id")
    private Long cohortId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_values", nullable = false, columnDefinition = "jsonb")
    private String valuesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConfigurationVersionStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "authorization_request_id")
    private Long authorizationRequestId;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "proposed_by_id", nullable = false)
    private Long proposedById;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "direct_publication", nullable = false)
    private boolean directPublication;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "configurationVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<ConfigurationVersionStatusChange> statusChanges = new ArrayList<>();

    public static ConfigurationVersion propose(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason,
                                               Long proposedById, Long authorizationRequestId, Long previousVersionId, Instant now) {
        ConfigurationVersion version = create(topic, values, effectiveFrom, changeReason, proposedById, previousVersionId, now);
        version.authorizationRequestId = authorizationRequestId;
        version.moveTo(ConfigurationVersionEvent.PROPOSE, StatusChangeActor.ofPerson(proposedById), now, changeReason);
        return version;
    }

    public static ConfigurationVersion publish(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason,
                                               Long publisherId, LocalDate today, Long previousVersionId, Instant now) {
        ConfigurationVersion version = create(topic, values, effectiveFrom, changeReason, publisherId, previousVersionId, now);
        version.directPublication = true;
        version.decidedById = publisherId;
        version.decidedAt = now;
        ConfigurationVersionEvent event = effectiveFrom.isAfter(today) ? ConfigurationVersionEvent.PUBLISH_FOR_FUTURE : ConfigurationVersionEvent.PUBLISH_FOR_TODAY;
        version.moveTo(event, StatusChangeActor.ofPerson(publisherId), now, changeReason);
        return version;
    }

    public void approve(Long deciderId, String decisionReason, LocalDate today, Instant now) {
        ConfigurationVersionEvent event = effectiveFrom.isAfter(today) ? ConfigurationVersionEvent.APPROVE_FOR_FUTURE : ConfigurationVersionEvent.APPROVE_FOR_TODAY;
        decide(event, deciderId, decisionReason, now);
    }

    public void reject(Long deciderId, String decisionReason, Instant now) {
        decide(ConfigurationVersionEvent.REJECT, deciderId, decisionReason, now);
    }

    public void withdraw(Long requesterId, Instant now) {
        moveTo(ConfigurationVersionEvent.WITHDRAW, StatusChangeActor.ofPerson(requesterId), now, null);
    }

    public void expire(Instant now) {
        moveTo(ConfigurationVersionEvent.EXPIRE, StatusChangeActor.ofProcess(EXPIRATION_PROCESS), now, null);
    }

    public void reachStart(Instant now) {
        moveTo(ConfigurationVersionEvent.REACH_START, StatusChangeActor.ofProcess(ACTIVATION_PROCESS), now, null);
    }

    public void supersede(Instant now) {
        moveTo(ConfigurationVersionEvent.SUPERSEDE, StatusChangeActor.ofProcess(ACTIVATION_PROCESS), now, null);
    }

    public void cancel(Long directorId, String reason, Instant now) {
        moveTo(ConfigurationVersionEvent.CANCEL, StatusChangeActor.ofPerson(directorId), now, reason);
        this.cancelledById = directorId;
        this.cancelledAt = now;
        this.cancellationReason = reason;
    }

    public ConfigurationValues values() {
        return ConfigurationValuesCodec.fromJson(valuesJson);
    }

    public boolean isOpenProposal() {
        return status == ConfigurationVersionStatus.PROPOSED;
    }

    public List<ConfigurationVersionStatusChange> getStatusChanges() {
        return List.copyOf(statusChanges);
    }

    private static ConfigurationVersion create(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason,
                                               Long proposedById, Long previousVersionId, Instant now) {
        if (values.scope() != topic.scope()) {
            throw new InvalidConfigurationValuesException("Los valores no corresponden al ámbito " + topic.scope());
        }
        ConfigurationVersion version = new ConfigurationVersion();
        version.scope = topic.scope();
        version.topicKey = topic.key();
        version.programId = topic.programId();
        version.termId = topic.termId();
        version.courseId = topic.courseId();
        version.cohortId = topic.cohortId();
        version.valuesJson = ConfigurationValuesCodec.toJson(values);
        version.effectiveFrom = effectiveFrom;
        version.changeReason = changeReason;
        version.proposedById = proposedById;
        version.proposedAt = now;
        version.previousVersionId = previousVersionId;
        return version;
    }

    private void decide(ConfigurationVersionEvent event, Long deciderId, String reason, Instant now) {
        moveTo(event, StatusChangeActor.ofPerson(deciderId), now, reason);
        this.decidedById = deciderId;
        this.decidedAt = now;
        this.decisionReason = reason;
    }

    private void moveTo(ConfigurationVersionEvent event, StatusChangeActor actor, Instant now, String reason) {
        ConfigurationVersionStatus target = ConfigurationVersionStateMachine.findTargetStateFor(status, event)
                .orElseThrow(() -> new IllegalConfigurationVersionStatusTransitionException(status, event));
        statusChanges.add(new ConfigurationVersionStatusChange(this, status, target, event, actor, now, reason));
        this.status = target;
    }
}
