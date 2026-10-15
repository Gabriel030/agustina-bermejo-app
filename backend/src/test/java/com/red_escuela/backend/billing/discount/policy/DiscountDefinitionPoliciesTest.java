package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.DiscountPolicySetsConfiguration;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.discount.exception.DiscountClosureReasonRequiredException;
import com.red_escuela.backend.billing.discount.exception.DiscountTypeNotInCatalogException;
import com.red_escuela.backend.billing.discount.exception.InstallmentCountOnlyForMonthlyInstallmentsException;
import com.red_escuela.backend.billing.discount.exception.InvalidDiscountDefinitionException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipOnlyForMonthlyInstallmentsException;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.InvalidPercentageException;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-075")
@Tag("D-129")
@Tag("D-157")
@Tag("D-171")
@Tag("D-283")
@Tag("D-287")
@Tag("D-097")
@Tag("D-217")
@Tag("D-240")
class DiscountDefinitionPoliciesTest {
    private static final PolicySet<DiscountDefinitionCheck> DEFINITION_POLICIES = new DiscountPolicySetsConfiguration().discountDefinitionPolicySet();
    private static final PolicySet<DiscountClosureCheck> CLOSURE_POLICIES = new DiscountPolicySetsConfiguration().discountDefinitionClosurePolicySet();

    private static List<PolicyViolation> violationsOf(DiscountDefinitionDraft draft) {
        return DEFINITION_POLICIES.evaluateAllRules(DiscountDefinitionCheck.withoutResolvedScope(draft)).violations();
    }

    private static List<PolicyViolation> violationsOf(DiscountDefinitionDraft draft, Currency offeringCurrency, Long cohortProgramId) {
        return DEFINITION_POLICIES.evaluateAllRules(new DiscountDefinitionCheck(draft, offeringCurrency, cohortProgramId)).violations();
    }

    private static List<String> codesOf(List<PolicyViolation> violations) {
        return violations.stream().map(PolicyViolation::code).toList();
    }

    @Test
    void aWellFormedCohortPromotionHasNoViolations() {
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().build())).isEmpty();
    }

    @Test
    void aWellFormedScholarshipWithoutCalculationHasNoViolations() {
        assertThat(violationsOf(DiscountDraftBuilder.scholarship().build())).isEmpty();
    }

    @Test
    void aWellFormedAnnualUpfrontHasNoViolations() {
        assertThat(violationsOf(DiscountDraftBuilder.annualUpfront().withPercent("15").build())).isEmpty();
    }

    @Test
    void aScholarshipMayCarryACalculation() {
        assertThat(violationsOf(DiscountDraftBuilder.scholarship().withPercent("50").build())).isEmpty();
    }

    @Test
    void zeroPercentIsRejected() {
        List<PolicyViolation> violations = violationsOf(DiscountDraftBuilder.cohortPromotion().withPercent("0").build());

        assertThat(codesOf(violations)).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violations.get(0).message()).isEqualTo("El porcentaje o el monto debe ser mayor a cero.");
    }

    @Test
    void zeroFixedAmountIsRejected() {
        List<PolicyViolation> violations = violationsOf(DiscountDraftBuilder.cohortPromotion().withFixedAmount("0.00", Currency.ARS).build());

        assertThat(codesOf(violations)).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aPercentageAboveOneHundredIsRejectedByTheValueObject() {
        assertThatThrownBy(() -> DiscountDraftBuilder.cohortPromotion().withPercent("100.01"))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void oneHundredPercentIsAccepted() {
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withPercent("100").build())).isEmpty();
    }

    @Test
    void aCalculationIsRequiredWhenTheTypeIsNotScholarship() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().withoutCalculation().build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aLateFeeInvoiceTypeIsRejected() {
        List<PolicyViolation> violations = violationsOf(DiscountDraftBuilder.cohortPromotion().forInvoiceType(InvoiceType.LATE_FEE).withoutInstallments().build());

        assertThat(codesOf(violations)).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violations.get(0).message()).isEqualTo("Ninguna promoción aplica a un recargo.");
    }

    @Test
    void aMissingInvoiceTypeIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().forInvoiceType(null).build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aMissingDiscountTypeIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().ofType(null).build();

        assertThat(codesOf(violationsOf(draft))).contains("INVALID_DISCOUNT_DEFINITION");
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceType.class, names = {"ENROLLMENT_FEE", "EXAM_FEE", "RETAKEN_SUBJECT_FEE", "DEGREE_ISSUANCE_FEE", "ADMINISTRATIVE_FEE", "ANNUAL_UPFRONT", "MONTHLY_INSTALLMENT"})
    void everyInvoiceTypeExceptLateFeeIsAccepted(InvoiceType invoiceType) {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().forInvoiceType(invoiceType).withoutInstallments().build();

        assertThat(violationsOf(draft)).isEmpty();
    }

    @Test
    void anAnnualUpfrontPromotionOnAMonthlyInstallmentIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.annualUpfront().forInvoiceType(InvoiceType.MONTHLY_INSTALLMENT).build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankNameIsRejected(String name) {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().named(name).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aMissingNameIsRejected() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().named(null).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aNameOfTwoHundredCharactersIsAcceptedAndOneMoreIsRejected() {
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().named("a".repeat(200)).build())).isEmpty();
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().named("a".repeat(201)).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aPriorityBelowOneIsRejected() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().withPriority(0).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withPriority(1).build())).isEmpty();
    }

    @Test
    void aQuotaBelowOneIsRejectedAndOneIsAccepted() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().withQuota(0).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withQuota(1).build())).isEmpty();
    }

    @Test
    void anInstallmentCountBelowOneIsRejectedAndOneIsAccepted() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().withInstallments(0).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withInstallments(1).build())).isEmpty();
    }

    @Test
    void aCapPerInvoiceMustBePositive() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().withCap("0.00", Currency.ARS).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().withCap("-1.00", Currency.ARS).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withCap("0.01", Currency.ARS).build())).isEmpty();
    }

    @Test
    void availableUntilBeforeAvailableFromIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().availableFrom(LocalDate.of(2031, 6, 1)).availableUntil(LocalDate.of(2031, 5, 31)).build();

        List<PolicyViolation> violations = violationsOf(draft);

        assertThat(codesOf(violations)).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violations.get(0).message()).isEqualTo("La disponibilidad hasta no puede ser anterior a la disponibilidad desde.");
    }

    @Test
    void availableUntilEqualToAvailableFromIsAccepted() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().availableFrom(LocalDate.of(2031, 6, 1)).availableUntil(LocalDate.of(2031, 6, 1)).build();

        assertThat(violationsOf(draft)).isEmpty();
    }

    @Test
    void aMissingAvailableFromIsRejected() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.cohortPromotion().availableFrom(null).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void anEnrollmentWindowThatEndsBeforeItStartsIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().withEnrollmentWindow(LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 9)).build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void anEnrollmentWindowOfASingleDayIsAccepted() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().withEnrollmentWindow(LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 10)).build();

        assertThat(violationsOf(draft)).isEmpty();
    }

    @Test
    void anOpenEnrollmentWindowIsAccepted() {
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withEnrollmentWindow(LocalDate.of(2031, 3, 10), null).build())).isEmpty();
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withEnrollmentWindow(null, LocalDate.of(2031, 3, 10)).build())).isEmpty();
    }

    @Test
    void aCohortPromotionNeedsExactlyOneOfProgramOrCourse() {
        DiscountDefinitionDraft none = DiscountDraftBuilder.cohortPromotion().withoutScope().build();
        DiscountDefinitionDraft program = DiscountDraftBuilder.cohortPromotion().forProgram(1L).build();
        DiscountDefinitionDraft course = DiscountDraftBuilder.cohortPromotion().forCourse(2L).build();
        DiscountDefinitionDraft both = new DiscountDefinitionDraft(program.name(), program.discountType(), program.applicableInvoiceType(), program.calculation(),
                program.installmentsTotal(), program.availableFrom(), program.availableUntil(), program.priority(), program.capPerInvoice(), 1L, null, 2L,
                null, null, null);

        assertThat(codesOf(violationsOf(none))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violationsOf(program)).isEmpty();
        assertThat(violationsOf(course)).isEmpty();
        assertThat(codesOf(violationsOf(both))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aCohortCanOnlyNarrowAProgram() {
        DiscountDefinitionDraft courseWithCohort = DiscountDraftBuilder.cohortPromotion().forCourse(2L).forCohort(7L).build();

        assertThat(codesOf(violationsOf(courseWithCohort))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aCohortThatBelongsToAnotherProgramIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().forProgram(1L).forCohort(7L).build();

        assertThat(codesOf(violationsOf(draft, Currency.ARS, 99L))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(violationsOf(draft, Currency.ARS, 1L)).isEmpty();
    }

    @Test
    void scopeWindowAndQuotaAreOnlyForCohortPromotions() {
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.annualUpfront().forProgram(1L).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.annualUpfront().forCourse(1L).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.annualUpfront().forCohort(1L).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.annualUpfront().withQuota(5).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.annualUpfront().withEnrollmentWindow(LocalDate.of(2031, 3, 1), null).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
        assertThat(codesOf(violationsOf(DiscountDraftBuilder.annualUpfront().withEnrollmentWindow(null, LocalDate.of(2031, 3, 1)).build()))).containsExactly("INVALID_DISCOUNT_DEFINITION");
    }

    @Test
    void aScholarshipOnAnEnrollmentFeeIsRejectedWithTheCreateAPromotionHint() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.scholarship().forInvoiceType(InvoiceType.ENROLLMENT_FEE).build();

        List<PolicyViolation> violations = violationsOf(draft);

        assertThat(codesOf(violations)).containsExactly("SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS");
        assertThat(violations.get(0).message()).contains("creá una promoción de tipo matrícula");
    }

    @Test
    void aCohortEntryTypeIsNotInTheCatalog() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().ofType(DiscountDefinitionType.COHORT_ENTRY).withoutScope().build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("DISCOUNT_TYPE_NOT_IN_CATALOG");
    }

    @Test
    void thePaymentMethodTypeIsNotOfferedUntilItsMechanismIsDecided() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().ofType(DiscountDefinitionType.PAYMENT_METHOD).withoutScope().build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("DISCOUNT_TYPE_NOT_IN_CATALOG");
    }

    @Test
    void anInstallmentCountOnAnEnrollmentFeeIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().forInvoiceType(InvoiceType.ENROLLMENT_FEE).withInstallments(3).build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS");
    }

    @Test
    void anOpenEndedInstallmentCountOnAnEnrollmentFeeIsAccepted() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().forInvoiceType(InvoiceType.ENROLLMENT_FEE).withoutInstallments().build();

        assertThat(violationsOf(draft)).isEmpty();
    }

    @Test
    void aFixedAmountInAnotherCurrencyThanTheOfferingIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().withFixedAmount("10000.00", Currency.ARS).build();

        assertThat(codesOf(violationsOf(draft, Currency.USD, null))).containsExactly("CURRENCY_MISMATCH");
        assertThat(violationsOf(draft, Currency.ARS, null)).isEmpty();
    }

    @Test
    void aCapInAnotherCurrencyThanTheOfferingIsRejected() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.cohortPromotion().withCap("5000.00", Currency.ARS).build();

        assertThat(codesOf(violationsOf(draft, Currency.USD, null))).containsExactly("CURRENCY_MISMATCH");
        assertThat(violationsOf(draft, Currency.ARS, null)).isEmpty();
    }

    @Test
    void aPercentageNeverMismatchesTheOfferingCurrency() {
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().build(), Currency.USD, null)).isEmpty();
    }

    @Test
    void withoutAResolvedOfferingCurrencyTheCurrencyIsNotChecked() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.annualUpfront().withFixedAmount("10000.00", Currency.USD).build();

        assertThat(violationsOf(draft)).isEmpty();
    }

    @Test
    void everyViolationOfARequestIsReportedAtOnce() {
        DiscountDefinitionDraft draft = DiscountDraftBuilder.scholarship().forInvoiceType(InvoiceType.ENROLLMENT_FEE).withInstallments(3).availableUntil(LocalDate.of(2020, 1, 1)).build();

        assertThat(codesOf(violationsOf(draft))).containsExactly("INVALID_DISCOUNT_DEFINITION", "SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS",
                "INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS");
    }

    @Test
    void theFirstViolationDecidesWhichExceptionIsThrown() {
        assertThat(DiscountDefinitionRejections.rejectionOf(List.of(new PolicyViolation("INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS", "x"))))
                .isInstanceOf(InstallmentCountOnlyForMonthlyInstallmentsException.class);
        assertThat(DiscountDefinitionRejections.rejectionOf(List.of(new PolicyViolation("SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS", "x"))))
                .isInstanceOf(ScholarshipOnlyForMonthlyInstallmentsException.class);
        assertThat(DiscountDefinitionRejections.rejectionOf(List.of(new PolicyViolation("DISCOUNT_TYPE_NOT_IN_CATALOG", "x"))))
                .isInstanceOf(DiscountTypeNotInCatalogException.class);
        assertThat(DiscountDefinitionRejections.rejectionOf(List.of(new PolicyViolation("CURRENCY_MISMATCH", "x"))))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThat(DiscountDefinitionRejections.rejectionOf(List.of(new PolicyViolation("DISCOUNT_CLOSURE_REASON_REQUIRED", "x"))))
                .isInstanceOf(DiscountClosureReasonRequiredException.class);
        assertThat(DiscountDefinitionRejections.rejectionOf(List.of(new PolicyViolation("INVALID_DISCOUNT_DEFINITION", "x"), new PolicyViolation("CURRENCY_MISMATCH", "y"))))
                .isInstanceOf(InvalidDiscountDefinitionException.class);
    }

    @Test
    void closingNeedsAReasonOfUpToOneThousandCharacters() {
        assertThat(CLOSURE_POLICIES.evaluateAllRules(new DiscountClosureCheck("Se agotó el cupo")).allowed()).isTrue();
        assertThat(CLOSURE_POLICIES.evaluateAllRules(new DiscountClosureCheck("a".repeat(1000))).allowed()).isTrue();
        assertThat(CLOSURE_POLICIES.evaluateAllRules(new DiscountClosureCheck("a".repeat(1001))).allowed()).isFalse();
        assertThat(CLOSURE_POLICIES.evaluateAllRules(new DiscountClosureCheck("  ")).allowed()).isFalse();
        assertThat(CLOSURE_POLICIES.evaluateAllRules(new DiscountClosureCheck(null)).violations().get(0).code()).isEqualTo("DISCOUNT_CLOSURE_REASON_REQUIRED");
    }

    @Test
    void theSmallestPositiveFixedAmountAndPercentAreAccepted() {
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withFixedAmount("0.01", Currency.ARS).build())).isEmpty();
        assertThat(violationsOf(DiscountDraftBuilder.cohortPromotion().withPercent("0.01").build())).isEmpty();
    }
}
