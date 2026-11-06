package com.red_escuela.backend.shared.exception;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeResponseDetailsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pendingDebtExposesTotalDueAndInstallmentCountUnderStableKeys() throws Exception {
        PendingDebtException exception = new PendingDebtException(7L, Money.of(new BigDecimal("33000.00"), Currency.ARS), 2);

        JsonNode body = mapper.readTree(mapper.writeValueAsString(ErrorCodeResponse.of(exception)));

        assertThat(PendingDebtException.TOTAL_DUE_KEY).isEqualTo("totalDue");
        assertThat(PendingDebtException.INSTALLMENT_COUNT_KEY).isEqualTo("installmentCount");
        assertThat(body.get("code").asText()).isEqualTo("PENDING_DEBT");
        assertThat(body.get("details").get("totalDue").decimalValue()).isEqualByComparingTo("33000.00");
        assertThat(body.get("details").get("installmentCount").asInt()).isEqualTo(2);
    }

    @Test
    void errorsWithoutDetailsOmitTheField() throws Exception {
        JsonNode body = mapper.readTree(mapper.writeValueAsString(new ErrorCodeResponse("VALIDATION_FAILED")));

        assertThat(body.has("details")).isFalse();
    }
}
