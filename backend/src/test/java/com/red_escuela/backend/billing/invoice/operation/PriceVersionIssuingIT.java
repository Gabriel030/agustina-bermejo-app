package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.pricing.exception.NoEffectivePriceException;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-214")
@Tag("D-035")
@Tag("D-036")
@Tag("D-240")
class PriceVersionIssuingIT extends AbstractInvoiceIssuingIT {

    @Autowired
    private PriceReader priceReader;

    private Invoice issueInstallment(IssuingScenarioFactory.Offering offering, IssuingScenarioFactory.Learner learner, YearMonth period, LocalDate pricingDate) {
        return issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, period, period.atDay(1).plusDays(10), pricingDate)));
    }

    private void addVersion(IssuingScenarioFactory.Offering offering, long amount, LocalDate from, String discountType, String discountValue) {
        PriceFixtures.persistPriceVersion(offering.programId(), null, FeeType.MONTHLY_INSTALLMENT, "Cuota", Money.of(BigDecimal.valueOf(amount), offering.currency()), from,
                offering.directorId(), discountType, discountValue == null ? null : new BigDecimal(discountValue));
    }

    private Map<String, Object> lineRowOf(Invoice invoice) {
        return jdbc.queryForMap("select amount, list_amount, marketing_discount_amount, price_version_id, amount_currency from invoice_line where id = ?", invoice.getLines().get(0).getId());
    }

    @Test
    @Tag("D-119")
    void aScheduledIncreaseChargesFromItsStartDayWithoutTouchingWhatWasAlreadyIssued() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        var learner = factory.newLearner(offering, null);
        addVersion(offering, 120000, LocalDate.of(2027, 9, 1), null, null);

        Invoice august = issueInstallment(offering, learner, YearMonth.of(2027, 8), LocalDate.of(2027, 8, 1));
        Invoice september = issueInstallment(offering, learner, YearMonth.of(2027, 9), LocalDate.of(2027, 9, 1));

        assertThat(august.getLines().get(0).getAmount()).isEqualTo(ars("100000.00"));
        assertThat(september.getLines().get(0).getAmount()).isEqualTo(ars("120000.00"));
        assertThat(september.getLines().get(0).getPriceVersionId()).isNotEqualTo(august.getLines().get(0).getPriceVersionId());
        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, september.getLines().get(0).getPriceVersionId())).isEqualTo("SCHEDULED");
        assertThat(lineRowOf(august).get("amount")).isEqualTo(new BigDecimal("100000.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-054")
    @Tag("D-161")
    void theRunPricingDateDecidesEvenWhenTheIncreaseStartsTwoDaysAfterIt() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        var learner = factory.newLearner(offering, null);
        addVersion(offering, 130000, LocalDate.of(2027, 4, 3), null, null);

        Invoice firstOfApril = issueInstallment(offering, learner, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 1));

        assertThat(firstOfApril.getLines().get(0).getAmount()).isEqualTo(ars("100000.00"));
        assertThat(priceReader.requirePriceVersionEffectiveOn(OfferingReference.ofProgram(offering.programId()), FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2027, 4, 3)).finalAmount())
                .isEqualTo(ars("130000.00"));
    }

    @Test
    void anOfferingWithoutAPriceForTheDateReportsNoEffectivePrice() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);

        assertThatThrownBy(() -> priceReader.requirePriceVersionEffectiveOn(OfferingReference.ofProgram(offering.programId()), FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2019, 12, 31)))
                .isInstanceOf(NoEffectivePriceException.class);
        assertThat(priceReader.findPriceVersionEffectiveOn(OfferingReference.ofProgram(offering.programId()), FeeType.EXAM_FEE, LocalDate.of(2027, 3, 1))).isEmpty();
    }

    @Test
    @Tag("D-097")
    void eachLineIsIssuedInTheCurrencyOfItsOffering() {
        var inPesos = factory.newProgram(Currency.ARS, 100000, 50000);
        var inDollars = factory.newProgram(Currency.USD, 300, 100);

        Invoice pesos = issueInstallment(inPesos, factory.newLearner(inPesos, null), YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));
        Invoice dollars = issueInstallment(inDollars, factory.newLearner(inDollars, null), YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));

        assertThat(lineRowOf(pesos).get("amount_currency")).isEqualTo("ARS");
        assertThat(lineRowOf(dollars).get("amount_currency")).isEqualTo("USD");
        assertThat(dollars.getLines().get(0).getAmount().value().amount()).isEqualByComparingTo("300.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theInvoiceChargesTheFinalPriceForAPercentageAFixedAndNoMarketingDiscount() {
        var percentage = factory.newProgram(Currency.ARS, 100000, 50000);
        var fixed = factory.newProgram(Currency.ARS, 100000, 50000);
        var none = factory.newProgram(Currency.ARS, 100000, 50000);
        addVersion(percentage, 100000, LocalDate.of(2027, 1, 1), "PERCENTAGE", "50");
        addVersion(fixed, 100000, LocalDate.of(2027, 1, 1), "FIXED", "30000");

        Invoice halfPrice = issueInstallment(percentage, factory.newLearner(percentage, null), YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));
        Invoice thirtyOff = issueInstallment(fixed, factory.newLearner(fixed, null), YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));
        Invoice listPrice = issueInstallment(none, factory.newLearner(none, null), YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));

        assertThat(halfPrice.getAmounts().total()).isEqualTo(ars("50000.00"));
        assertThat(thirtyOff.getAmounts().total()).isEqualTo(ars("70000.00"));
        assertThat(listPrice.getAmounts().total()).isEqualTo(ars("100000.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theLineKeepsListPriceMarketingDiscountAndFinalPriceEvenAfterANewVersionWithoutDiscount() {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        var learner = factory.newLearner(offering, null);
        addVersion(offering, 100000, LocalDate.of(2027, 1, 1), "PERCENTAGE", "50");
        Invoice campaign = issueInstallment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));
        addVersion(offering, 100000, LocalDate.of(2027, 4, 1), null, null);
        Invoice afterCampaign = issueInstallment(offering, learner, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 1));

        Map<String, Object> campaignLine = lineRowOf(campaign);
        assertThat((BigDecimal) campaignLine.get("list_amount")).isEqualByComparingTo("100000.00");
        assertThat((BigDecimal) campaignLine.get("marketing_discount_amount")).isEqualByComparingTo("50000.00");
        assertThat((BigDecimal) campaignLine.get("amount")).isEqualByComparingTo("50000.00");
        assertThat(lineRowOf(afterCampaign).get("price_version_id")).isNotEqualTo(campaignLine.get("price_version_id"));
        assertThat(jdbc.queryForObject("select sum(l.marketing_discount_amount) from invoice_line l where l.id in (?, ?)", BigDecimal.class,
                campaign.getLines().get(0).getId(), afterCampaign.getLines().get(0).getId())).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
