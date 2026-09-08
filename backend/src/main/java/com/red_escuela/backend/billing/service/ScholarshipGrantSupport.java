package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.exception.ScholarshipExceedsCohortSpanException;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Service
public class ScholarshipGrantSupport {

    private final ScholarshipRepository scholarshipRepository;
    private final CreditNoteService creditNoteService;

    public ScholarshipGrantSupport(ScholarshipRepository scholarshipRepository, CreditNoteService creditNoteService) {
        this.scholarshipRepository = scholarshipRepository;
        this.creditNoteService = creditNoteService;
    }

    public Scholarship build(CreateScholarshipRequest request, Person actor) {
        Scholarship scholarship = new Scholarship();
        scholarship.setValue(request.value().toValue());
        scholarship.setReason(request.reason());
        scholarship.setGrantedBy(actor);
        scholarship.addInstallments(request.installments());
        return scholarship;
    }

    public void validateInstallmentsWithinCohortSpan(int installments, Integer maxInstallments) {
        if (maxInstallments != null && installments > maxInstallments) {
            throw new ScholarshipExceedsCohortSpanException(installments, maxInstallments);
        }
    }

    // Una beca otorgada después de que ya se generó y pagó la cuota del período corriente no
    // puede recuperar ese descuento vía Scholarship.consumeOne (esa Invoice ya está PAID, no se
    // reabre) — en vez de eso se consume igual la primera installment (mismo criterio de conteo
    // que si el descuento se hubiera aplicado en el momento) y la diferencia se devuelve como
    // CreditNote en lugar de un ajuste sobre la Invoice.
    public void applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(Scholarship scholarship, List<Invoice> paidInvoices, Student student) {
        YearMonth currentPeriod = YearMonth.now();
        paidInvoices.stream()
                .filter(invoice -> invoice.getFeeType() == FeeType.RECURRING_FEE && currentPeriod.equals(invoice.getPeriod()))
                .findFirst()
                .ifPresent(invoice -> {
                    scholarship.consumeOne(invoice);
                    scholarshipRepository.save(scholarship);
                    // La installment se consume igual (arriba) aunque el descuento resuelva a $0 —
                    // cuenta como "ya aplicada" a todos los efectos de la beca — pero solo si hay
                    // plata real de por medio nace una CreditNote.
                    BigDecimal creditAmount = scholarship.getValue().resolve(invoice.getBaseAmount());
                    if (creditAmount.signum() > 0) {
                        var creditNote = creditNoteService.createFor(student, creditAmount, CreditNoteReasonType.RETROACTIVE_SCHOLARSHIP,
                                scholarship.getReason(), scholarship.getGrantedBy());
                        creditNote.getSourceInvoices().add(invoice);
                    }
                });
    }
}
