package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListCreditNotesOfStudentService implements ListCreditNotesOfStudentUseCase {
    private final CreditNoteRepository creditNoteRepository;

    public ListCreditNotesOfStudentService(CreditNoteRepository creditNoteRepository) {
        this.creditNoteRepository = creditNoteRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditNote> listCreditNotesOfStudent(Long studentId) {
        return creditNoteRepository.findByStudentIdNewestFirst(studentId);
    }
}
