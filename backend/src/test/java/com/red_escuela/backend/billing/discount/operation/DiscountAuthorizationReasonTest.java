package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.exception.CurrencyMismatchException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-217")
class DiscountAuthorizationReasonTest {
    private static final int AUTHORIZATION_REASON_COLUMN_LENGTH = 500;
    private static final int LONGEST_CLOSURE_REASON = 450;
    private static final int LONGEST_NAME = 200;

    @Test
    void theClosureRequestReasonUsesTheTrimmedReasonAndFitsTheColumnForTheLargestId() {
        String reason = " ".repeat(300) + "r".repeat(LONGEST_CLOSURE_REASON) + " ".repeat(300);

        String authorizationReason = new CloseDiscountDefinitionCommand(Long.MAX_VALUE, reason, 1L, false).authorizationReason();

        assertThat(authorizationReason).isEqualTo("Cierre de la promoción " + Long.MAX_VALUE + ": " + "r".repeat(LONGEST_CLOSURE_REASON));
        assertThat(authorizationReason.length()).isLessThanOrEqualTo(AUTHORIZATION_REASON_COLUMN_LENGTH);
    }

    @Test
    void theCreationRequestReasonUsesTheTrimmedNameAndFitsTheColumn() {
        String name = " ".repeat(400) + "n".repeat(LONGEST_NAME) + " ".repeat(400);

        String authorizationReason = CreateDiscountDefinitionCommand.byPerson(DiscountDraftBuilder.cohortPromotion().named(name).build(), 1L, false).authorizationReason();

        assertThat(authorizationReason).isEqualTo("Alta de la promoción " + "n".repeat(LONGEST_NAME));
        assertThat(authorizationReason.length()).isLessThanOrEqualTo(AUTHORIZATION_REASON_COLUMN_LENGTH);
    }

    @Test
    @Tag("D-097")
    void theCurrencyMismatchOfPricingAndOfDiscountsAnswerTheSameCode() {
        String discountCode = ErrorCodeResponse.of(new CurrencyMismatchException(List.of())).code();
        String pricingCode = ErrorCodeResponse.of(new com.red_escuela.backend.billing.pricing.exception.CurrencyMismatchException(List.of())).code();

        assertThat(discountCode).isEqualTo("CURRENCY_MISMATCH");
        assertThat(pricingCode).isEqualTo(discountCode);
    }
}
