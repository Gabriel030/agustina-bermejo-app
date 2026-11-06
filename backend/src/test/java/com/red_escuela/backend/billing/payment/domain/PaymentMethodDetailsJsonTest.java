package com.red_escuela.backend.billing.payment.domain;

import tools.jackson.databind.json.JsonMapper;
import com.red_escuela.backend.billing.operation.CardPaymentType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-079")
@Tag("D-082")
class PaymentMethodDetailsJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    static Stream<PaymentMethodDetails> everyVariant() {
        return Stream.of(
                new CashPaymentDetails("R-100"),
                new BankTransferPaymentDetails("Banco Nación", "0110000000000000000001", "OP-9", "Ana Pérez", LocalDate.of(2027, 3, 10)),
                new CardPaymentDetails(CardPaymentType.CREDIT, 3, "4242"),
                new WalletPaymentDetails("MERCADO_PAGO_WALLET", "payer@example.com"));
    }

    @ParameterizedTest
    @MethodSource("everyVariant")
    void everyVariantSurvivesAJsonRoundTripAsItself(PaymentMethodDetails details) throws Exception {
        String json = mapper.writeValueAsString(details);

        assertThat(mapper.readValue(json, PaymentMethodDetails.class)).isEqualTo(details);
        assertThat(mapper.readTree(json).get("type").asString("")).isEqualTo(details.methodType().name());
    }

    @Test
    void theCashDetailsCarryNeitherInstallmentsNorCardType() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new CashPaymentDetails("R-100")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("type", "receiptNumber");
    }

    @Test
    void theTransferDateIsWrittenAsAnIsoDate() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(everyVariant().toList().get(1)));

        assertThat(json.get("transferDate").asString("")).isEqualTo("2027-03-10");
    }
}
