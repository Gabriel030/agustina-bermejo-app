package com.red_escuela.backend.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.red_escuela.backend.billing.dto.LatePaymentSettingsResponse;
import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.PaymentAllocationResponse;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import com.red_escuela.backend.shared.serialization.ValueObjectJacksonModule;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingMoneyContractTest {

    @RestController
    static class ContractController {
        @GetMapping("/contract/allocation")
        PaymentAllocationResponse allocation() {
            return new PaymentAllocationResponse(1L, ars(48000));
        }

        @PostMapping("/contract/allocation")
        PaymentAllocationRequest allocation(@Valid @RequestBody PaymentAllocationRequest request) {
            return request;
        }

        @PostMapping("/contract/policy")
        CreateLatePaymentSettingsRequest policy(@Valid @RequestBody CreateLatePaymentSettingsRequest request) {
            return request;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new ValueObjectJacksonModule())
            .registerModule(new JavaTimeModule());

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void anAmountTravelsAsAStringWithItsCurrency() throws Exception {
        mockMvc.perform(get("/contract/allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountApplied.amount").value("48000.00"))
                .andExpect(jsonPath("$.amountApplied.currency").value("ARS"));
    }

    @Test
    void aRequestWithAnAmountAndCurrencyIsAccepted() throws Exception {
        mockMvc.perform(post("/contract/allocation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":1,\"amountApplied\":{\"amount\":\"100.00\",\"currency\":\"USD\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountApplied.currency").value("USD"));
    }

    @Test
    @Tag("D-098")
    void aRequestWithAnAmountWithoutCurrencyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/contract/allocation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":1,\"amountApplied\":{\"amount\":\"100.00\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRequestWithANonPositiveAmountIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/contract/allocation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":1,\"amountApplied\":{\"amount\":\"0.00\",\"currency\":\"ARS\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("D-099")
    void aSurchargePercentOfTenIsKeptAsTenAndOneHundredFiftyIsRejectedWith400() throws Exception {
        String body = "{\"gracePeriodDays\":10,\"dailyLateFeeRatePercent\":%s,\"suspensionAfterDays\":30,"
                + "\"suspensionAfterInvoiceCount\":2}";
        mockMvc.perform(post("/contract/policy").contentType(MediaType.APPLICATION_JSON).content(body.formatted("10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLateFeeRatePercent").value(10));
        mockMvc.perform(post("/contract/policy").contentType(MediaType.APPLICATION_JSON).content(body.formatted("150")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERCENTAGE"));
    }

    @Test
    @Tag("D-330")
    void aProposalWithTheMonthlyRateInsteadOfTheDailyOneIsRejectedWith400() throws Exception {
        String monthly = "{\"gracePeriodDays\":10,\"monthlyLateFeeRate\":{\"fraction\":0.05},\"suspensionAfterDays\":30,\"suspensionAfterInvoiceCount\":2}";
        mockMvc.perform(post("/contract/policy").contentType(MediaType.APPLICATION_JSON).content(monthly))
                .andExpect(status().isBadRequest());
        String withoutRate = "{\"gracePeriodDays\":10,\"suspensionAfterDays\":30,\"suspensionAfterInvoiceCount\":2}";
        mockMvc.perform(post("/contract/policy").contentType(MediaType.APPLICATION_JSON).content(withoutRate))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownCurrencyIsRejectedWith400AndItsCode() throws Exception {
        mockMvc.perform(post("/contract/allocation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":1,\"amountApplied\":{\"amount\":\"100.00\",\"currency\":\"EUR\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    @Tag("D-099")
    void thePolicyResponseExposesThePercentInPoints() throws Exception {
        String json = objectMapper.writeValueAsString(new LatePaymentSettingsResponse(
                1L, 10, com.red_escuela.backend.shared.domain.Percentage.ofPoints(new java.math.BigDecimal("10")),
                30, 2, java.time.LocalDate.of(2026, 1, 1)));
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(json).get("dailyLateFeeRatePercent").decimalValue())
                .isEqualByComparingTo("10");
    }
}
