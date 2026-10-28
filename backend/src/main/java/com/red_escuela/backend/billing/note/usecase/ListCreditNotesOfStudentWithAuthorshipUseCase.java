package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;

import java.util.List;

public interface ListCreditNotesOfStudentWithAuthorshipUseCase {
    List<CreditNoteResponse> listCreditNotesOfStudentWithAuthorship(Long studentId);
}
