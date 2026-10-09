package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CreditApplication;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.CreditNoteStatus;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.exception.CreditNoteNotFoundException;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditNoteService {
    private final CreditNoteRepository creditNoteRepository;

    public CreditNoteService(CreditNoteRepository creditNoteRepository) {
        this.creditNoteRepository = creditNoteRepository;
    }

    @Transactional(readOnly = true)
    public Money availableBalance(Long studentId, Currency currency) {
        return creditNoteRepository.findByStudentIdAndStatusNotOrderByCreatedAtAsc(studentId, CreditNoteStatus.CONSUMED).stream()
                .filter(note -> note.getAmount().currency() == currency)
                .map(CreditNote::getRemainingAmount)
                .reduce(Money.zero(currency), Money::plus);
    }

    @Transactional
    public CreditNote createFor(Student student, Money amount, CreditNoteReasonType reasonType, String reason, Person createdBy) {
        CreditNote creditNote = new CreditNote();
        creditNote.setStudent(student);
        creditNote.setAmount(amount);
        creditNote.setReasonType(reasonType);
        creditNote.setReason(reason);
        creditNote.setCreatedBy(createdBy);
        return creditNoteRepository.save(creditNote);
    }

    @Transactional
    public List<CreditApplication> consumeForInvoice(Long studentId, Money amount, Invoice invoice, Payment payment) {
        List<Long> candidateIds = creditNoteRepository.findIdsByStudentIdAndStatusNotOrderByCreatedAtAsc(studentId, CreditNoteStatus.CONSUMED);

        List<CreditApplication> applications = new ArrayList<>();
        Money remaining = amount;
        for (Long id : candidateIds) {
            if (remaining.isZero()) {
                break;
            }
            CreditNote locked = creditNoteRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new CreditNoteNotFoundException(id));
            if (locked.getAmount().currency() != amount.currency()) {
                continue;
            }
            Money noteRemaining = locked.getRemainingAmount();
            if (noteRemaining.isZero()) {
                continue;
            }
            Money toApply = noteRemaining.isGreaterThan(remaining) ? remaining : noteRemaining;
            applications.add(locked.consume(toApply, invoice, payment));
            creditNoteRepository.save(locked);
            remaining = remaining.minus(toApply);
        }
        if (remaining.isGreaterThan(Money.zero(amount.currency()))) {
            throw new InsufficientCreditBalanceException(studentId, amount, amount.minus(remaining));
        }
        return applications;
    }
}
