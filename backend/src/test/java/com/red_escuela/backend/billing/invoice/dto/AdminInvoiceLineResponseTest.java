package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.issuedMonthly;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-127")
class AdminInvoiceLineResponseTest {

    @Test
    void theCreditableAmountOfALineIsWhatTheCreditNotePolicyAcceptsForThatLine() {
        Invoice invoice = issuedMonthly("1000.00");

        AdminInvoiceLineResponse response = AdminInvoiceLineResponse.from(invoice.getLines().get(0));

        assertThat(response.creditableAmount().amount()).isEqualByComparingTo("1000.00");
        assertThat(response.creditableAmount()).isEqualTo(invoice.getLines().get(0).calculateCreditableAmount().value());
        assertThat(response.lineNumber()).isEqualTo(invoice.getLines().get(0).getLineNumber());
    }
}
