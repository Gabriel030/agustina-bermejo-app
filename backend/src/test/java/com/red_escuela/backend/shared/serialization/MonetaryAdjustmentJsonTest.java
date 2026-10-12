package com.red_escuela.backend.shared.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
class MonetaryAdjustmentJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    private final MonetaryAdjustment nominal =
            new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("5000"), Currency.ARS));
    private final MonetaryAdjustment proportional =
            new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("10")));

    @Test
    void nominalIsSerializedWithTypeAndMoney() throws Exception {
        assertThat(objectMapper.writeValueAsString(nominal))
                .isEqualTo("{\"type\":\"NOMINAL\",\"amount\":{\"amount\":\"5000.00\",\"currency\":\"ARS\"}}");
    }

    @Test
    void proportionalIsSerializedWithTypeAndRatePercent() throws Exception {
        assertThat(objectMapper.writeValueAsString(proportional))
                .isEqualTo("{\"type\":\"PROPORTIONAL\",\"ratePercent\":10}");
    }

    @Test
    void bothVariantsRoundTrip() throws Exception {
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(nominal), MonetaryAdjustment.class))
                .isEqualTo(nominal);
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(proportional), MonetaryAdjustment.class))
                .isEqualTo(proportional);
    }

    @Test
    void ratePercentWithDecimalsIsReadAsPoints() throws Exception {
        assertThat(objectMapper.readValue("{\"type\":\"PROPORTIONAL\",\"ratePercent\":12.5}", MonetaryAdjustment.class))
                .isEqualTo(new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.125"))));
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"type\":\"OTHER\"}", MonetaryAdjustment.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void ratePercentAsTextIsRejected() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"type\":\"PROPORTIONAL\",\"ratePercent\":\"10\"}",
                MonetaryAdjustment.class))
                .isInstanceOf(MismatchedInputException.class);
    }
}
