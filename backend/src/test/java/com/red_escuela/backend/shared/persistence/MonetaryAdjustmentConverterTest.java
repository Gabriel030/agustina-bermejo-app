package com.red_escuela.backend.shared.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;

class MonetaryAdjustmentConverterTest {

    private final MonetaryAdjustmentConverter converter = new MonetaryAdjustmentConverter(new ObjectMapper());

    @Test
    void readsTheJsonProducedByTheBillingMigration() {
        assertThat(converter.convertToEntityAttribute("{\"type\":\"NOMINAL\",\"amount\":\"5000.00\",\"currency\":\"ARS\"}"))
                .isEqualTo(new MonetaryAdjustment.Nominal(ars(5000)));
        assertThat(converter.convertToEntityAttribute("{\"type\":\"PROPORTIONAL\",\"fraction\":\"0.3000\"}"))
                .isEqualTo(new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.30"))));
    }

    @Test
    void writesAndReadsBackTheNewFormatKeepingTheCurrency() {
        MonetaryAdjustment nominal = new MonetaryAdjustment.Nominal(usd(120));
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("12.5")));

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(nominal))).isEqualTo(nominal);
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(proportional))).isEqualTo(proportional);
    }
}
