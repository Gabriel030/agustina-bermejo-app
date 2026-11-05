package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-092")
class IssueCreditNoteCommandFingerprintTest {

    private static IssueCreditNoteCommand command(String amount, CreditNoteDestination destination, boolean director, Long authorizationRequestId) {
        return new IssueCreditNoteCommand(7L, CreditNoteReason.BILLING_ERROR, "Error", List.of(new CreditNoteLineAmount(1, Money.of(new BigDecimal(amount), Currency.ARS))),
                destination, 9L, director, authorizationRequestId, "key");
    }

    @Test
    void m2u01FieldsDerivedByTheServerDoNotChangeTheFingerprintOfTheSameRequest() {
        IssueCreditNoteCommand original = command("100.00", CreditNoteDestination.DEBT_OFFSET, false, null);

        assertThat(command("100.00", CreditNoteDestination.DEBT_OFFSET, true, 55L).requestFingerprint()).isEqualTo(original.requestFingerprint());
        assertThat(original.resumedFromAuthorizationRequest(55L).requestFingerprint()).isEqualTo(original.requestFingerprint());
    }

    @Test
    void m2u02AnyFieldTheClientChoosesChangesTheFingerprint() {
        IssueCreditNoteCommand original = command("100.00", CreditNoteDestination.DEBT_OFFSET, false, null);

        assertThat(command("100.01", CreditNoteDestination.DEBT_OFFSET, false, null).requestFingerprint()).isNotEqualTo(original.requestFingerprint());
        assertThat(new IssueCreditNoteCommand(8L, CreditNoteReason.BILLING_ERROR, "Error", original.lines(), CreditNoteDestination.DEBT_OFFSET, 9L, false, null, "key")
                .requestFingerprint()).isNotEqualTo(original.requestFingerprint());
        assertThat(new IssueCreditNoteCommand(7L, CreditNoteReason.BILLING_ERROR, "Other", original.lines(), CreditNoteDestination.DEBT_OFFSET, 9L, false, null, "key")
                .requestFingerprint()).isNotEqualTo(original.requestFingerprint());
    }
}
