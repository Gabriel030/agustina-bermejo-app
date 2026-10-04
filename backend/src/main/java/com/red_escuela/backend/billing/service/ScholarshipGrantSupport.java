package com.red_escuela.backend.billing.service;

import java.time.Clock;
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

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import java.time.YearMonth;
import java.util.List;

@Service
public class ScholarshipGrantSupport {
    private final ScholarshipRepository scholarshipRepository;
    private final CreditNoteService creditNoteService;
    private final Clock clock;

    public ScholarshipGrantSupport(ScholarshipRepository scholarshipRepository, CreditNoteService creditNoteService, Clock clock) {
        this.scholarshipRepository = scholarshipRepository;
        this.creditNoteService = creditNoteService;
        this.clock = clock;
    }

    public Scholarship build(CreateScholarshipRequest request, Person actor, Currency currency) {
        MonetaryAdjustment value = request.value().toAdjustment();
        value.requireCurrency(currency);
        Scholarship scholarship = new Scholarship();
        scholarship.setValue(value);
        scholarship.setReason(request.reason());
        scholarship.setGrantedBy(actor);
        if (request.appliesTo() != null) {
            scholarship.setAppliesTo(request.appliesTo());
        }
        scholarship.addInstallments(request.installments());
        return scholarship;
    }

    public void validateInstallmentsWithinCohortSpan(int installments, Integer maxInstallments) {
        if (maxInstallments != null && installments > maxInstallments) {
            throw new ScholarshipExceedsCohortSpanException(installments, maxInstallments);
        }
    }

    public void applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(Scholarship scholarship, List<Invoice> paidInvoices, Student student) {
        YearMonth currentPeriod = YearMonth.now(clock);
        paidInvoices.stream()
                .filter(invoice -> invoice.getFeeType() == FeeType.RECURRING_FEE && currentPeriod.equals(invoice.getPeriod()))
                .findFirst()
                .ifPresent(invoice -> {
                    scholarship.consumeOne(invoice);
                    scholarshipRepository.save(scholarship);
                    Money creditAmount = scholarship.getValue().applyTo(invoice.getBaseAmount());
                    if (!creditAmount.isZero() && !creditAmount.isNegative()) {
                        var creditNote = creditNoteService.createFor(student, creditAmount, CreditNoteReasonType.RETROACTIVE_SCHOLARSHIP,
                                scholarship.getReason(), scholarship.getGrantedBy());
                        creditNote.getSourceInvoices().add(invoice);
                    }
                });
    }
}
