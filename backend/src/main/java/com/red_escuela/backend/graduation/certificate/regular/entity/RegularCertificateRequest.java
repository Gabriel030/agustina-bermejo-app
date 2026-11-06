package com.red_escuela.backend.graduation.certificate.regular.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "regular_certificate_request")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegularCertificateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private RegularityKind kind;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20, updatable = false)
    private RegularCertificateChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private RegularRequestOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @Column(name = "certificate_id")
    private Long certificateId;

    public static RegularCertificateRequest record(RegularityKind kind, Long enrollmentId, Long studentId, Long requestedById,
                                                   LocalDateTime requestedAt, RegularCertificateChannel channel,
                                                   RegularRequestOutcome outcome, List<String> reasons) {
        return RegularCertificateRequest.builder()
                .studentId(studentId)
                .kind(kind)
                .programEnrollmentId(kind == RegularityKind.PROGRAM ? enrollmentId : null)
                .courseEnrollmentId(kind == RegularityKind.COURSE ? enrollmentId : null)
                .requestedById(requestedById)
                .requestedAt(requestedAt)
                .channel(channel)
                .outcome(outcome)
                .reasons(List.copyOf(reasons))
                .build();
    }

    public void linkCertificate(Long newCertificateId) {
        this.certificateId = newCertificateId;
    }
}
