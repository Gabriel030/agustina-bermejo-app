package com.red_escuela.backend.architecture.fixture.billing.collections;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.service.LateFeeAnnulmentOnInstallmentCancellation;

import java.util.List;

public class LateFeeAnnulmentShortcutFixture {

    public List<CreditNote> annulWithoutTheCancellation(LateFeeAnnulmentOnInstallmentCancellation annulment, Invoice installment) {
        return annulment.annulLiveLateFeesOfInstallment(installment);
    }
}
