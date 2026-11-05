package com.red_escuela.backend.billing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-382")
class DailyLateFeeConversionRuleTest {

    private static final Pattern CONVERSION = Pattern.compile("round\\(\\(config_values -> 'monthlyLateFeeRate' ->> 'fraction'\\)::numeric / (\\d+), (\\d+)\\)");

    private static String migration() throws IOException {
        return Files.readString(Path.of("src/main/resources/db/migration/V110__billing_v2_daily_late_fee.sql"));
    }

    @Test
    void migrationConvertsTheMonthlyRateDividingByThirtyRoundedToFourDecimals() throws IOException {
        Matcher conversion = CONVERSION.matcher(migration());

        assertThat(conversion.find()).isTrue();
        BigDecimal divisor = new BigDecimal(conversion.group(1));
        int scale = Integer.parseInt(conversion.group(2));
        assertThat(new BigDecimal("0.0500").divide(divisor, scale, RoundingMode.HALF_UP)).isEqualByComparingTo("0.0017");
        assertThat(new BigDecimal("0.0400").divide(divisor, scale, RoundingMode.HALF_UP)).isEqualByComparingTo("0.0013");
    }

    @Test
    void migrationLeavesTheChargingDisabledUntilTheDirectorReviewsTheRate() throws IOException {
        assertThat(migration()).contains("'lateFeeChargingEnabled', false");
    }

    @Test
    void migrationRequiresAnEmptyLateFeeChargeTable() throws IOException {
        assertThat(migration()).contains("if exists (select 1 from late_fee_charge) then").contains("raise exception");
    }
}
