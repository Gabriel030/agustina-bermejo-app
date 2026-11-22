package com.red_escuela.backend.academicstructure.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.module.BillingMode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GetCatalogPriceVisibilityServiceTest {

    private static final Money PRICE = Money.of(new BigDecimal("1500.00"), Currency.ARS);

    @Test
    void billingOffHidesCatalogPrices() {
        assertThat(new GetCatalogPriceVisibilityService(() -> BillingMode.OFF).execute()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BillingMode.class, names = {"MIRROR", "FULL"})
    void billingEnabledShowsCatalogPrices(BillingMode mode) {
        assertThat(new GetCatalogPriceVisibilityService(() -> mode).execute()).isTrue();
    }

    @Test
    void programResponseWithoutPricesKeepsTheRest() {
        ProgramResponse response = ProgramResponse.builder().id(1L).name("Carrera").price(PRICE).enrollmentFeeAmount(PRICE).build().withoutPrices();

        assertThat(response.getPrice()).isNull();
        assertThat(response.getEnrollmentFeeAmount()).isNull();
        assertThat(response.getName()).isEqualTo("Carrera");
    }

    @Test
    void courseResponseWithoutPricesKeepsTheRest() {
        CourseResponse response = CourseResponse.builder().id(2L).name("Curso").price(PRICE).build().withoutPrices();

        assertThat(response.getPrice()).isNull();
        assertThat(response.getName()).isEqualTo("Curso");
    }
}
