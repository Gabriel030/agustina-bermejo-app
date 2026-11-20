package com.red_escuela.backend.graduation.certificate.regular.entity;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "certificate_exception_status_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateExceptionStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "approval_id", nullable = false)
    private CertificateExceptionApproval approval;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private CertificateExceptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private CertificateExceptionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 20)
    private CertificateExceptionEvent event;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "actor_process", length = 80)
    private String actorProcess;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "reason", length = 1000)
    private String reason;

    CertificateExceptionStatusChange(CertificateExceptionApproval approval, CertificateExceptionStatus fromStatus,
                                     CertificateExceptionStatus toStatus, CertificateExceptionEvent event, StatusChangeActor actor,
                                     LocalDateTime changedAt, String reason) {
        this.approval = approval;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.event = event;
        this.actorPersonId = actor.personId();
        this.actorProcess = actor.processName();
        this.changedAt = changedAt;
        this.reason = reason;
    }
}
