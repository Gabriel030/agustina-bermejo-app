package com.red_escuela.backend.shared.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
class MoneyJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void moneyIsSerializedWithTheAmountAsAStringWithTwoDecimals() throws Exception {
        String json = objectMapper.writeValueAsString(Money.of(new BigDecimal("48000"), Currency.ARS));
        assertThat(json).isEqualTo("{\"amount\":\"48000.00\",\"currency\":\"ARS\"}");
    }

    @Test
    void moneyRoundTripsToTheSameValue() throws Exception {
        Money money = objectMapper.readValue("{ \"amount\": \"48000.00\", \"currency\": \"ARS\" }", Money.class);
        assertThat(money).isEqualTo(Money.of(new BigDecimal("48000"), Currency.ARS));
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(money), Money.class)).isEqualTo(money);
    }

    @Test
    void dollarsRoundTripKeepingTheCurrency() throws Exception {
        Money dollars = Money.of(new BigDecimal("150.5"), Currency.USD);
        String json = objectMapper.writeValueAsString(dollars);
        assertThat(json).isEqualTo("{\"amount\":\"150.50\",\"currency\":\"USD\"}");
        assertThat(objectMapper.readValue(json, Money.class)).isEqualTo(dollars);
    }

    @Test
    void moneyWithoutCurrencyIsRejected() {
        assertThatThrownBy(() -> objectMapper.readValue("{ \"amount\": \"10.00\" }", Money.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class);
    }

    @Test
    void largeAmountsAreNotWrittenInScientificNotation() throws Exception {
        String json = objectMapper.writeValueAsString(Money.of(new BigDecimal("1E+7"), Currency.ARS));
        assertThat(json).isEqualTo("{\"amount\":\"10000000.00\",\"currency\":\"ARS\"}");
    }

    @Test
    void percentageIsSerializedAsPointsNumber() throws Exception {
        assertThat(objectMapper.writeValueAsString(Percentage.ofFraction(new BigDecimal("0.875")))).isEqualTo("87.5");
        assertThat(objectMapper.writeValueAsString(Percentage.ofPoints(new BigDecimal("10")))).isEqualTo("10");
        assertThat(objectMapper.writeValueAsString(Percentage.ofPoints(BigDecimal.ZERO))).isEqualTo("0");
    }

    @Test
    void percentageIsReadFromPoints() throws Exception {
        assertThat(objectMapper.readValue("87.5", Percentage.class))
                .isEqualTo(Percentage.ofFraction(new BigDecimal("0.875")));
    }

    @Test
    void emailAddressIsAPlainNormalizedString() throws Exception {
        assertThat(objectMapper.writeValueAsString(EmailAddress.of("ana@mail.com"))).isEqualTo("\"ana@mail.com\"");
        assertThat(objectMapper.readValue("\" Ana@Mail.com \"", EmailAddress.class))
                .isEqualTo(EmailAddress.of("ana@mail.com"));
    }
}
