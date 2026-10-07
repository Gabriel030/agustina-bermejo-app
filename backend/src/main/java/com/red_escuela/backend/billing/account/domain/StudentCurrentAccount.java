package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

@Entity
@Table(name = "student_current_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentCurrentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Embedded
    private CurrentAccountBalance balance;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "unallocated_credit_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "unallocated_credit_amount_currency", nullable = false))})
    private NonNegativeMoney unallocatedCreditAmount;

    @Column(name = "last_entry_sequence", nullable = false)
    private long lastEntrySequence;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    private StudentCurrentAccount(Long studentId, Currency currency) {
        this.studentId = studentId;
        this.currency = currency;
        this.balance = CurrentAccountBalance.zero(currency);
        this.unallocatedCreditAmount = NonNegativeMoney.zero(currency);
        this.lastEntrySequence = 0;
        this.openedAt = ApplicationTime.now();
    }

    public static StudentCurrentAccount openStudentCurrentAccount(Long studentId, Currency currency) {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
        return new StudentCurrentAccount(studentId, currency);
    }
}
