package com.red_escuela.backend.billing.usecase.creditnote;

import com.red_escuela.backend.billing.entity.CreditNote;

import java.util.List;

public interface ListCreditNotesForStudentUseCase {
    List<CreditNote> list(Long studentId);
}
