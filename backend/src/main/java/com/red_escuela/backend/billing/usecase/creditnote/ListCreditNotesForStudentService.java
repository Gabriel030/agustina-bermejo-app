package com.red_escuela.backend.billing.usecase.creditnote;

import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListCreditNotesForStudentService implements ListCreditNotesForStudentUseCase {

    private final CreditNoteRepository creditNoteRepository;
    private final StudentRepository studentRepository;

    public ListCreditNotesForStudentService(CreditNoteRepository creditNoteRepository, StudentRepository studentRepository) {
        this.creditNoteRepository = creditNoteRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditNote> list(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException(studentId);
        }
        return creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }
}
