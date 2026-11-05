package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptLimits;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-267")
@Tag("D-097")
class BankTransferReceiptDetailsRequiredPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);
    private final BankTransferReceiptDetailsRequiredPolicy policy = new BankTransferReceiptDetailsRequiredPolicy();

    private static BankTransferReceiptDetailsCheck valid() {
        return new BankTransferReceiptDetailsCheck(Money.of(new BigDecimal("80000.00"), Currency.ARS), TODAY, TODAY, "OP-123", "0170099220000067797370",
                "Ana Pérez", "Banco Nación", true, 1);
    }

    private static BankTransferReceiptDetailsCheck with(Money amount, LocalDate transferDate, String operationNumber, String originAccount, String holderName,
                                                        String bankName, boolean hasFile, int invoices) {
        return new BankTransferReceiptDetailsCheck(amount, transferDate, TODAY, operationNumber, originAccount, holderName, bankName, hasFile, invoices);
    }

    static Stream<Arguments> missingFields() {
        Money amount = Money.of(new BigDecimal("1.00"), Currency.ARS);
        return Stream.of(
                Arguments.of("null amount", with(null, TODAY, "OP", "CBU", "Ana", null, true, 1), "BANK_TRANSFER_AMOUNT_INVALID"),
                Arguments.of("zero amount", with(Money.zero(Currency.ARS), TODAY, "OP", "CBU", "Ana", null, true, 1), "BANK_TRANSFER_AMOUNT_INVALID"),
                Arguments.of("negative amount", with(Money.of(new BigDecimal("-1.00"), Currency.ARS), TODAY, "OP", "CBU", "Ana", null, true, 1),
                        "BANK_TRANSFER_AMOUNT_INVALID"),
                Arguments.of("amount above what the column stores", with(Money.of(new BigDecimal("10000000000.00"), Currency.ARS), TODAY, "OP", "CBU", "Ana", null, true, 1),
                        "BANK_TRANSFER_AMOUNT_INVALID"),
                Arguments.of("no transfer date", with(amount, null, "OP", "CBU", "Ana", null, true, 1), "BANK_TRANSFER_DATE_REQUIRED"),
                Arguments.of("null operation number", with(amount, TODAY, null, "CBU", "Ana", null, true, 1), "BANK_TRANSFER_OPERATION_NUMBER_REQUIRED"),
                Arguments.of("blank operation number", with(amount, TODAY, "   ", "CBU", "Ana", null, true, 1), "BANK_TRANSFER_OPERATION_NUMBER_REQUIRED"),
                Arguments.of("null origin account", with(amount, TODAY, "OP", null, "Ana", null, true, 1), "BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED"),
                Arguments.of("blank origin account", with(amount, TODAY, "OP", " ", "Ana", null, true, 1), "BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED"),
                Arguments.of("null holder", with(amount, TODAY, "OP", "CBU", null, null, true, 1), "BANK_TRANSFER_HOLDER_NAME_REQUIRED"),
                Arguments.of("blank holder", with(amount, TODAY, "OP", "CBU", "", null, true, 1), "BANK_TRANSFER_HOLDER_NAME_REQUIRED"),
                Arguments.of("no file", with(amount, TODAY, "OP", "CBU", "Ana", null, false, 1), "BANK_TRANSFER_RECEIPT_FILE_REQUIRED"),
                Arguments.of("no invoices", with(amount, TODAY, "OP", "CBU", "Ana", null, true, 0), "BANK_TRANSFER_INVOICES_REQUIRED"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingFields")
    void rejectsEachMissingFieldWithItsOwnViolation(String description, BankTransferReceiptDetailsCheck check, String expectedCode) {
        PolicyVerdict verdict = policy.evaluateRule(check);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo(expectedCode);
    }

    @Test
    void acceptsAFullyFilledReceiptAndAnOptionalBlankBankName() {
        assertThat(policy.evaluateRule(valid()).allowed()).isTrue();
        assertThat(policy.evaluateRule(with(Money.of(new BigDecimal("1.00"), Currency.ARS), TODAY, "OP", "CBU", "Ana", null, true, 1)).allowed()).isTrue();
    }

    @Test
    void rejectsFutureTransferDate() {
        PolicyVerdict verdict = policy.evaluateRule(with(Money.of(new BigDecimal("1.00"), Currency.ARS), TODAY.plusDays(1), "OP", "CBU", "Ana", null, true, 1));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("BANK_TRANSFER_DATE_IN_FUTURE");
    }

    @Test
    void acceptsATransferDateOfTodayAndOfAnyPastDay() {
        Money amount = Money.of(new BigDecimal("1.00"), Currency.ARS);

        assertThat(policy.evaluateRule(with(amount, TODAY, "OP", "CBU", "Ana", null, true, 1)).allowed()).isTrue();
        assertThat(policy.evaluateRule(with(amount, TODAY.minusDays(30), "OP", "CBU", "Ana", null, true, 1)).allowed()).isTrue();
    }

    @Test
    void acceptsMaximumColumnLengths() {
        Money amount = Money.of(new BigDecimal("9999999999.99"), Currency.ARS);

        PolicyVerdict verdict = policy.evaluateRule(with(amount, TODAY, "9".repeat(BankTransferReceiptLimits.MAX_OPERATION_NUMBER_LENGTH),
                "9".repeat(BankTransferReceiptLimits.MAX_ORIGIN_ACCOUNT_LENGTH), "A".repeat(BankTransferReceiptLimits.MAX_HOLDER_NAME_LENGTH),
                "B".repeat(BankTransferReceiptLimits.MAX_BANK_NAME_LENGTH), true, 1));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void acceptsMaximumLengthsPaddedWithSpaces() {
        Money amount = Money.of(new BigDecimal("1.00"), Currency.ARS);

        PolicyVerdict verdict = policy.evaluateRule(with(amount, TODAY, " " + "9".repeat(BankTransferReceiptLimits.MAX_OPERATION_NUMBER_LENGTH) + " ", "CBU", "Ana",
                null, true, 1));

        assertThat(verdict.allowed()).isTrue();
    }

    static Stream<Arguments> oneCharacterOver() {
        Money amount = Money.of(new BigDecimal("1.00"), Currency.ARS);
        return Stream.of(
                Arguments.of("operation number", with(amount, TODAY, "9".repeat(BankTransferReceiptLimits.MAX_OPERATION_NUMBER_LENGTH + 1), "CBU", "Ana", null, true, 1),
                        "BANK_TRANSFER_OPERATION_NUMBER_TOO_LONG"),
                Arguments.of("origin account", with(amount, TODAY, "OP", "9".repeat(BankTransferReceiptLimits.MAX_ORIGIN_ACCOUNT_LENGTH + 1), "Ana", null, true, 1),
                        "BANK_TRANSFER_ORIGIN_ACCOUNT_TOO_LONG"),
                Arguments.of("holder", with(amount, TODAY, "OP", "CBU", "A".repeat(BankTransferReceiptLimits.MAX_HOLDER_NAME_LENGTH + 1), null, true, 1),
                        "BANK_TRANSFER_HOLDER_NAME_TOO_LONG"),
                Arguments.of("bank name", with(amount, TODAY, "OP", "CBU", "Ana", "B".repeat(BankTransferReceiptLimits.MAX_BANK_NAME_LENGTH + 1), true, 1),
                        "BANK_TRANSFER_BANK_NAME_TOO_LONG"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneCharacterOver")
    void rejectsOneCharacterOverTheColumnLength(String description, BankTransferReceiptDetailsCheck check, String expectedCode) {
        PolicyVerdict verdict = policy.evaluateRule(check);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo(expectedCode);
    }
}
