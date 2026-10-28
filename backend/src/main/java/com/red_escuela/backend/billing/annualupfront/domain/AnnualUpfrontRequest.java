package com.red_escuela.backend.billing.annualupfront.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "annual_upfront_request")
public class AnnualUpfrontRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "program_enrollment_id", nullable = false, updatable = false)
    private Long programEnrollmentId;

    @Column(name = "lective_year_term_id", nullable = false, updatable = false)
    private Long lectiveYearTermId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnnualUpfrontRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "discard_reason")
    private AnnualUpfrontDiscardReason discardReason;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "requested_by_person_id", nullable = false, updatable = false)
    private Long requestedByPersonId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "accepted_terms_version", nullable = false, updatable = false)
    private String acceptedTermsVersion;

    @Column(name = "accepted_terms_text", nullable = false, updatable = false)
    private String acceptedTermsText;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by_person_id", nullable = false, updatable = false)
    private Long acceptedByPersonId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public static AnnualUpfrontRequest registerRequest(AnnualUpfrontRequestRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        AnnualUpfrontRequest request = new AnnualUpfrontRequest();
        request.status = AnnualUpfrontRequestStateMachine.requireTargetFor(null, AnnualUpfrontRequestEvent.REQUESTED);
        request.programEnrollmentId = registration.programEnrollmentId();
        request.lectiveYearTermId = registration.lectiveYearTermId();
        request.invoiceId = registration.invoiceId();
        request.requestedByPersonId = registration.personId();
        request.acceptedByPersonId = registration.personId();
        request.requestedAt = registration.requestedAt();
        request.acceptedAt = registration.requestedAt();
        request.acceptedTermsVersion = registration.terms().version();
        request.acceptedTermsText = registration.terms().text();
        return request;
    }
}
