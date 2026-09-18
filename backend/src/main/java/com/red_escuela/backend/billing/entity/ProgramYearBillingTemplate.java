package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateExceedsTermSpanException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.YearMonth;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;

@Getter
@Setter
@Entity
@Table(name = "program_year_billing_template", uniqueConstraints = @UniqueConstraint(columnNames = {"program_id", "term_id"}))
public class ProgramYearBillingTemplate {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(name = "recurring_fee_invoices_count", nullable = false)
    private Integer recurringFeeInvoicesCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateRecurringFeeInvoicesCountAgainstTermSpan();
    }

    @PreUpdate
    void onUpdate() {
        validateRecurringFeeInvoicesCountAgainstTermSpan();
    }

    private void validateRecurringFeeInvoicesCountAgainstTermSpan() {
        Long termSpanMonths = term.spanInMonths();
        if (termSpanMonths != null && recurringFeeInvoicesCount != null && recurringFeeInvoicesCount > termSpanMonths) {
            throw new ProgramYearBillingTemplateExceedsTermSpanException(recurringFeeInvoicesCount, termSpanMonths);
        }
    }

    public boolean canGenerateInvoice(YearMonth period, long invoicesGeneratedInWindow) {
        return term.contains(period) && invoicesGeneratedInWindow < recurringFeeInvoicesCount;
    }

    public int remainingInvoices(long invoicesGeneratedInWindow) {
        return recurringFeeInvoicesCount - (int) invoicesGeneratedInWindow;
    }
}
