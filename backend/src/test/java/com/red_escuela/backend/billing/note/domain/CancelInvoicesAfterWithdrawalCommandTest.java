package com.red_escuela.backend.billing.note.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-204")
class CancelInvoicesAfterWithdrawalCommandTest {
    private static final LocalDate WITHDRAWAL = LocalDate.of(2027, 5, 15);

    @Test
    void anInvoiceCannotAppearTwiceInTheSameWithdrawalCancellation() {
        assertThatThrownBy(() -> new CancelInvoicesAfterWithdrawalCommand(
                List.of(WithdrawalCancellationItem.cancelling(1L), WithdrawalCancellationItem.cancelling(1L)), WITHDRAWAL, 2L, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWithdrawalCancellationNeedsAtLeastOneInvoice() {
        assertThatThrownBy(() -> new CancelInvoicesAfterWithdrawalCommand(List.of(), WITHDRAWAL, 2L, false, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
