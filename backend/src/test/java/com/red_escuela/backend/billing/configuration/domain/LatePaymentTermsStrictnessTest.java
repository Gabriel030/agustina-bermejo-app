package com.red_escuela.backend.billing.configuration.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatePaymentTermsStrictnessTest {
    private static final String WITH_CAP = """
            {"type":"LATE_PAYMENT","gracePeriodDays":5,"dailyLateFeeRate":{"fraction":0.002},"lateFeeCapMonths":12,
             "suspensionAfterDays":30,"suspensionAfterInvoiceCount":1,"blockNewEnrollmentsWhenOverdue":true,"lateFeeDebitNoteDueDays":10,
             "freezeLateFeesOnWithdrawal":true,"lateFeeChargingEnabled":true}
            """;

    @Test
    @Tag("D-239")
    void aCapIsRejectedEvenByAMapperThatIgnoresUnknownProperties() {
        ObjectMapper lenient = JsonMapper.builder().findAndAddModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        assertThatThrownBy(() -> lenient.readValue(WITH_CAP, LatePaymentTerms.class)).hasRootCauseInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Test
    @Tag("D-239")
    void theCodecRejectsAVersionWithACapAndRoundTripsOneWithout() {
        assertThatThrownBy(() -> ConfigurationValuesCodec.fromJson(WITH_CAP)).isInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(InvalidConfigurationValuesException.class);
        LatePaymentTerms terms = (LatePaymentTerms) ConfigurationValuesCodec.fromJson(WITH_CAP.replace("\"lateFeeCapMonths\":12,", ""));
        assertThat(terms.lateFeeChargingEnabled()).isTrue();
        assertThat(ConfigurationValuesCodec.fromJson(ConfigurationValuesCodec.toJson(terms))).isEqualTo(terms);
    }

    @Test
    @Tag("D-210")
    void theInitialTermsStartWithChargingDisabled() {
        assertThat(LatePaymentTerms.initial().lateFeeChargingEnabled()).isFalse();
    }

    @Test
    @Tag("D-330")
    void latePaymentTermsRequireTheDailyRateAndRejectTheMonthlyOne() {
        String monthly = WITH_CAP.replace("\"dailyLateFeeRate\"", "\"monthlyLateFeeRate\"").replace("\"lateFeeCapMonths\":12,", "");
        String withoutRate = WITH_CAP.replace("\"dailyLateFeeRate\":{\"fraction\":0.002},", "").replace("\"lateFeeCapMonths\":12,", "");
        assertThatThrownBy(() -> ConfigurationValuesCodec.fromJson(monthly)).isInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> ConfigurationValuesCodec.fromJson(withoutRate)).isInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(InvalidConfigurationValuesException.class);
        ObjectMapper lenient = JsonMapper.builder().findAndAddModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        assertThatThrownBy(() -> lenient.readValue(monthly, LatePaymentTerms.class)).hasRootCauseInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Test
    @Tag("D-510")
    void aVersionWithoutThePendingReceiptCapReadsTenAndTheCapIsReadWhenPresent() {
        String withoutCap = WITH_CAP.replace("\"lateFeeCapMonths\":12,", "");
        LatePaymentTerms defaulted = (LatePaymentTerms) ConfigurationValuesCodec.fromJson(withoutCap);
        LatePaymentTerms configured = (LatePaymentTerms) ConfigurationValuesCodec.fromJson(withoutCap.replace("\"lateFeeChargingEnabled\":true", "\"lateFeeChargingEnabled\":true,\"pendingReceiptProtectionBusinessDays\":4"));
        assertThat(defaulted.pendingReceiptProtectionBusinessDays()).isEqualTo(10);
        assertThat(LatePaymentTerms.initial().pendingReceiptProtectionBusinessDays()).isEqualTo(10);
        assertThat(configured.pendingReceiptProtectionBusinessDays()).isEqualTo(4);
    }

    @Test
    @Tag("D-510")
    void thePendingReceiptCapMustBeAtLeastOneBusinessDay() {
        String zero = WITH_CAP.replace("\"lateFeeCapMonths\":12,", "").replace("\"lateFeeChargingEnabled\":true", "\"lateFeeChargingEnabled\":true,\"pendingReceiptProtectionBusinessDays\":0");
        assertThatThrownBy(() -> ConfigurationValuesCodec.fromJson(zero)).isInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(InvalidConfigurationValuesException.class);
    }
}
