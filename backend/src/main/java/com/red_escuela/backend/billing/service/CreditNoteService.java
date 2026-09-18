package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CreditApplication;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.CreditNoteStatus;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.exception.CreditNoteNotFoundException;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditNoteService {
    private final CreditNoteRepository creditNoteRepository;

    public CreditNoteService(CreditNoteRepository creditNoteRepository) {
        this.creditNoteRepository = creditNoteRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal availableBalance(Long studentId) {
        return creditNoteRepository.findByStudentIdAndStatusNotOrderByCreatedAtAsc(studentId, CreditNoteStatus.CONSUMED).stream()
                .map(CreditNote::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public CreditNote createFor(Student student, BigDecimal amount, CreditNoteReasonType reasonType, String reason, Person createdBy) {
        CreditNote creditNote = new CreditNote();
        creditNote.setStudent(student);
        creditNote.setAmount(amount);
        creditNote.setReasonType(reasonType);
        creditNote.setReason(reason);
        creditNote.setCreatedBy(createdBy);
        return creditNoteRepository.save(creditNote);
    }

    @Transactional
    public List<CreditApplication> consumeForInvoice(Long studentId, BigDecimal amount, Invoice invoice, Payment payment) {
        List<Long> candidateIds = creditNoteRepository.findIdsByStudentIdAndStatusNotOrderByCreatedAtAsc(studentId, CreditNoteStatus.CONSUMED);

        List<CreditApplication> applications = new ArrayList<>();
        BigDecimal remaining = amount;
        for (Long id : candidateIds) {
            if (remaining.signum() == 0) {
                break;
            }
            CreditNote locked = creditNoteRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new CreditNoteNotFoundException(id));
            BigDecimal noteRemaining = locked.getRemainingAmount();
            if (noteRemaining.signum() == 0) {
                continue;
            }
            BigDecimal toApply = noteRemaining.min(remaining);
            applications.add(locked.consume(toApply, invoice, payment));
            creditNoteRepository.save(locked);
            remaining = remaining.subtract(toApply);
        }
        if (remaining.signum() > 0) {
            throw new InsufficientCreditBalanceException(studentId, amount, amount.subtract(remaining));
        }
        return applications;
    }
}
