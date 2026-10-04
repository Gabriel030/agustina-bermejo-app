package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.shared.time.ApplicationTime;
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

    @Column(name = "enrollment_fee_count", nullable = false)
    private Integer enrollmentFeeCount = 1;

    @Column(name = "first_enrollment_fee_month", nullable = false)
    private Integer firstEnrollmentFeeMonth = 2;

    @Column(name = "second_enrollment_fee_month")
    private Integer secondEnrollmentFeeMonth;

    @Column(name = "enrollment_fee_lead_days", nullable = false)
    private Integer enrollmentFeeLeadDays = 15;

    @Column(name = "blocks_enrollment", nullable = false)
    private boolean blocksEnrollment = true;

    @Column(name = "blocks_exam_registration", nullable = false)
    private boolean blocksExamRegistration = true;

    @Column(name = "blocks_certificates", nullable = false)
    private boolean blocksCertificates = true;

    @Column(name = "require_enrollment_fee_for_final_exam_only_students", nullable = false)
    private boolean requireEnrollmentFeeForFinalExamOnlyStudents = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void configureEnrollmentFees(int count, int firstMonth, Integer secondMonth) {
        if (count != 1 && count != 2) {
            throw new com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException("La cantidad de matrículas debe ser 1 o 2");
        }
        if (firstMonth < 1 || firstMonth > 12) {
            throw new com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException("El mes de matrícula debe estar entre 1 y 12");
        }
        if (count == 1) {
            this.enrollmentFeeCount = 1;
            this.firstEnrollmentFeeMonth = firstMonth;
            this.secondEnrollmentFeeMonth = null;
            return;
        }
        if (secondMonth == null || secondMonth <= firstMonth || secondMonth > 12) {
            throw new com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException("Con 2 matrículas, el mes de la segunda debe ser posterior al de la primera");
        }
        this.enrollmentFeeCount = 2;
        this.firstEnrollmentFeeMonth = firstMonth;
        this.secondEnrollmentFeeMonth = secondMonth;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = ApplicationTime.now();
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
