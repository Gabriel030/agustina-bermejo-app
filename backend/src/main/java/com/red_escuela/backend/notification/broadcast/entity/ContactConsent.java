package com.red_escuela.backend.notification.broadcast.entity;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.ConsentStatus;
import jakarta.persistence.Column;
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

import java.time.Instant;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "contact_consent")
public class ContactConsent {

    public static final int MAX_EVIDENCE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private BroadcastCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ConsentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ConsentSource source;

    @Column(name = "evidence", length = MAX_EVIDENCE_LENGTH)
    private String evidence;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public void recordDecision(ConsentStatus newStatus, ConsentSource newSource, String newEvidence, Instant at) {
        this.status = newStatus;
        this.source = newSource;
        this.evidence = newEvidence;
        this.recordedAt = at;
    }
}
