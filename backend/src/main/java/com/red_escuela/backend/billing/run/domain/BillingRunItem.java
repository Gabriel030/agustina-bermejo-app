package com.red_escuela.backend.billing.run.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "billing_run_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingRunItem {

    public static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "billing_run_id", nullable = false, updatable = false)
    private Long billingRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private BillingRunSubjectType subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private BillingRunItemStatus status;

    @Column(name = "decision_code", updatable = false)
    private String decisionCode;

    @Column(name = "error_code", updatable = false)
    private String errorCode;

    @Column(name = "message", length = MESSAGE_MAX_LENGTH, updatable = false)
    private String message;

    public static BillingRunItem recordItemOf(Long billingRunId, BillingRunItemRecord record) {
        BillingRunItem item = new BillingRunItem();
        item.billingRunId = Objects.requireNonNull(billingRunId, "billingRunId");
        item.subjectType = record.subjectType();
        item.subjectId = record.subjectId();
        item.status = record.status();
        item.decisionCode = record.decisionCode();
        item.errorCode = record.errorCode();
        item.message = truncate(record.message());
        return item;
    }

    private static String truncate(String message) {
        return message == null || message.length() <= MESSAGE_MAX_LENGTH ? message : message.substring(0, MESSAGE_MAX_LENGTH);
    }
}
