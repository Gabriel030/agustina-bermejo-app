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
            {"type":"LATE_PAYMENT","gracePeriodDays":5,"monthlyLateFeeRate":{"fraction":0.05},"lateFeeCapMonths":12,
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
}
