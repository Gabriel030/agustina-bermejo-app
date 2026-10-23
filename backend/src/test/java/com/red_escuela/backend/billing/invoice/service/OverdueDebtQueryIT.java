package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-264")
@SlowTest(reason = "Boots the first Spring context of its JVM when run alone, so its time includes the one-time start-up")
class OverdueDebtQueryIT extends AbstractCurrentAccountIT {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);

    @Autowired
    InvoiceFollowUpQueries queries;

    private boolean hasOverdueDebtWith(String paymentStatus, LocalDate dueDate) {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(accountId, "1000.00", dueDate);
        switch (paymentStatus) {
            case "PAID" -> jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            case "CANCELLED" -> jdbc.update("update invoice set payment_status = 'CANCELLED', credited_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            default -> jdbc.update("update invoice set payment_status = ? where id = ?", paymentStatus, invoiceId);
        }
        return queries.hasUnpaidIssuedDocumentOverdue(accountId, TODAY);
    }

    @ParameterizedTest
    @CsvSource({"PENDING,true", "OVERDUE,true", "PAYMENT_UNDER_REVIEW,true", "PAID,false", "CANCELLED,false"})
    void onlyUnpaidStatusesCountAsOverdueDebtWhenTheDueDateHasPassed(String paymentStatus, boolean blocks) {
        assertThat(hasOverdueDebtWith(paymentStatus, TODAY.minusDays(1))).isEqualTo(blocks);
    }

    @ParameterizedTest
    @CsvSource({"PENDING", "OVERDUE", "PAYMENT_UNDER_REVIEW"})
    void aDocumentDueTodayOrLaterIsNeverOverdueDebt(String paymentStatus) {
        assertThat(hasOverdueDebtWith(paymentStatus, TODAY)).isFalse();
        assertThat(hasOverdueDebtWith(paymentStatus, TODAY.plusDays(30))).isFalse();
    }

    @Test
    void theDebtOfAnotherAccountDoesNotCount() {
        Long other = newAccountId(newStudentId(), Currency.ARS);
        issueAdministrativeFeeWithLedgerDebit(other, "1000.00", TODAY.minusDays(10));
        Long clean = newAccountId(newStudentId(), Currency.ARS);

        assertThat(queries.hasUnpaidIssuedDocumentOverdue(clean, TODAY)).isFalse();
        assertThat(queries.hasUnpaidIssuedDocumentOverdue(other, TODAY)).isTrue();
    }
}
