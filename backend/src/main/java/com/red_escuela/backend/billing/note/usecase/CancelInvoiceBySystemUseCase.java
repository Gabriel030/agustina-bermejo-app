package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;

public interface CancelInvoiceBySystemUseCase {
    CreditNote cancelInvoiceWithoutDirector(Long invoiceId, SystemCancellationOrigin origin, String justification);
}
