package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.authorization.service.DirectorAuthorizationHandler;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-253")
@Tag("D-165")
class DirectorAuthorizationHandlerCreditNoteTest {

    private final DirectorAuthorizationHandler handler = new DirectorAuthorizationHandler();

    private static IssueCreditNoteCommand command(boolean requestedByDirector) {
        return new IssueCreditNoteCommand(5L, CreditNoteReason.BILLING_ERROR, "Error", List.of(new CreditNoteLineAmount(1, Money.of(BigDecimal.TEN, Currency.ARS))),
                CreditNoteDestination.DEBT_OFFSET, 9L, requestedByDirector, null);
    }

    @Test
    void anAdministrativeCreditNoteRequestNeedsTheDirectorAndNeverExpires() {
        PreProcessingResult result = handler.handleBeforeProcessing(BillingOperationType.ISSUE_CREDIT_NOTE, command(false), PriorAuthorization.ABSENT);

        assertThat(result).isInstanceOf(PreProcessingResult.RequiresAuthorization.class);
        assertThat(((PreProcessingResult.RequiresAuthorization) result).expiresOn()).isNull();
    }

    @Test
    void theDirectorOrAnApprovedRequestProceedsWithoutAuthorization() {
        assertThat(handler.handleBeforeProcessing(BillingOperationType.ISSUE_CREDIT_NOTE, command(true), PriorAuthorization.ABSENT)).isEqualTo(PreProcessingResult.proceed());
        assertThat(handler.handleBeforeProcessing(BillingOperationType.ISSUE_CREDIT_NOTE, command(false), PriorAuthorization.APPROVED)).isEqualTo(PreProcessingResult.proceed());
    }
}
