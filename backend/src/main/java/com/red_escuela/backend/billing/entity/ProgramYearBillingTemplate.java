package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateExceedsTermSpanException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.YearMonth;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.Term;

// One template per program per Term: how many monthly installments that year's cohorts have.
// Admission/cursada dates live in ProgramCohort, not here (2026-09-03 decision).
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

    // Invariante propia de la plantilla (decisión 2026-09-03): un pago nunca puede cubrir más de
    // un cohorte (ej. no adelantar un año entero de una carrera cuatrimestral, porque serían 2
    // cohortes). PaymentPlanSelectionService acota el scope UPFRONT contra recurringFeeInvoicesCount,
    // pero eso solo protege si esa cifra refleja fielmente la duración real del Term — se valida
    // acá, en el propio ciclo de vida de la entidad, para que sea imposible persistir una
    // plantilla inválida sin importar qué código la haya construido.
    private void validateRecurringFeeInvoicesCountAgainstTermSpan() {
        Long termSpanMonths = term.spanInMonths();
        if (termSpanMonths != null && recurringFeeInvoicesCount != null && recurringFeeInvoicesCount > termSpanMonths) {
            throw new ProgramYearBillingTemplateExceedsTermSpanException(recurringFeeInvoicesCount, termSpanMonths);
        }
    }

    // true si todavía se puede generar una cuota RECURRING_FEE para este período: cae dentro de mi
    // Term y no se superó mi recurringFeeInvoicesCount contando solo lo ya generado dentro de esa
    // misma ventana (el caller es responsable de calcular ese conteo — la plantilla no conoce
    // Invoice, eso sería acoplar el dominio de facturación al de calendario académico).
    public boolean canGenerateInvoice(YearMonth period, long invoicesGeneratedInWindow) {
        return term.contains(period) && invoicesGeneratedInWindow < recurringFeeInvoicesCount;
    }

    public int remainingInvoices(long invoicesGeneratedInWindow) {
        return recurringFeeInvoicesCount - (int) invoicesGeneratedInWindow;
    }
}
