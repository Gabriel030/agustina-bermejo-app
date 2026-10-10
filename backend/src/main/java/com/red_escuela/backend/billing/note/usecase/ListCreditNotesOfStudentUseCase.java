package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;

import java.util.List;

public interface ListCreditNotesOfStudentUseCase {
    List<CreditNote> listCreditNotesOfStudent(Long studentId);
}
