package com.red_escuela.backend.billing.usecase.creditnote;

import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.service.CreditNoteService;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateManualCreditNoteService implements CreateManualCreditNoteUseCase {
    private final StudentRepository studentRepository;
    private final CurrentPersonService currentPersonService;
    private final CreditNoteService creditNoteService;

    public CreateManualCreditNoteService(StudentRepository studentRepository, CurrentPersonService currentPersonService,
                                          CreditNoteService creditNoteService) {
        this.studentRepository = studentRepository;
        this.currentPersonService = currentPersonService;
        this.creditNoteService = creditNoteService;
    }

    @Override
    @Transactional
    public CreditNote create(Long studentId, CreateCreditNoteRequest request) {
        if (request.reasonType() != CreditNoteReasonType.ADMINISTRATIVE_CORRECTION && request.reasonType() != CreditNoteReasonType.OTHER) {
            throw new InvalidCreditNoteReasonException(request.reasonType());
        }
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        return creditNoteService.createFor(student, request.amount(), request.reasonType(), request.reason(), currentPersonService.resolveCurrentPerson());
    }
}
