package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StudentCreditNoteListingReader {

    private final CreditNoteRepository creditNoteRepository;

    public StudentCreditNoteListingReader(CreditNoteRepository creditNoteRepository) {
        this.creditNoteRepository = creditNoteRepository;
    }

    @Transactional(readOnly = true)
    public List<CreditNoteResponse> listCreditNotesOfStudent(Long studentId) {
        return creditNoteRepository.findByStudentIdNewestFirst(studentId).stream().map(CreditNoteResponse::forStudent).toList();
    }
}
