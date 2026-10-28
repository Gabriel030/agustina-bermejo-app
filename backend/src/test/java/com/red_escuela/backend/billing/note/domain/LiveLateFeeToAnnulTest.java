package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.issuedMonthly;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-291")
class LiveLateFeeToAnnulTest {

    @Test
    void everyOpenDebitNoteIsAnnulledForItsFullCreditableAmount() {
        Invoice first = issuedMonthly("1000.00");
        Invoice second = issuedMonthly("250.00");

        List<LiveLateFeeToAnnul> lateFees = LiveLateFeeToAnnul.amongDebitNotes(List.of(first, second));

        assertThat(lateFees).hasSize(2);
        assertThat(lateFees.get(0).debitNote()).isSameAs(first);
        assertThat(lateFees.get(0).amountToAnnul().value().amount()).isEqualByComparingTo("1000.00");
        assertThat(lateFees.get(1).amountToAnnul().value().amount()).isEqualByComparingTo("250.00");
    }

    @Test
    void noDebitNotesMeansNothingToAnnul() {
        assertThat(LiveLateFeeToAnnul.amongDebitNotes(List.of())).isEmpty();
    }
}
