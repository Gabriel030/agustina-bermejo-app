package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValueObjectExceptionHttpMappingTest {

    @RestController
    static class ValueObjectThrowingHandler {
        @GetMapping("/value-object-exception/{kind}")
        void raise(@PathVariable String kind) {
            switch (kind) {
                case "percentage" -> Percentage.ofPoints(new BigDecimal("150"));
                case "currency" -> Money.zero(Currency.ARS).plus(Money.zero(Currency.USD));
                case "date-range" -> new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));
                case "grade" -> Grade.of(new BigDecimal("10.20"));
                case "email" -> EmailAddress.of("not-an-email");
                case "adjustment" -> new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("-1"), Currency.ARS));
                default -> throw new UnsupportedOperationException(kind);
            }
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ValueObjectThrowingHandler())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @CsvSource({
            "percentage, INVALID_PERCENTAGE",
            "currency, CURRENCY_MISMATCH",
            "date-range, INVALID_DATE_RANGE",
            "grade, INVALID_GRADE",
            "email, INVALID_EMAIL_ADDRESS",
            "adjustment, NEGATIVE_ADJUSTMENT_AMOUNT"
    })
    void valueObjectViolationIsABadRequestWithItsOwnCode(String kind, String code) throws Exception {
        mockMvc.perform(get("/value-object-exception/" + kind))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }
}
