package com.red_escuela.backend.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.red_escuela.backend.billing.dto.AdminInvoiceResponse;
import com.red_escuela.backend.billing.dto.MeInvoiceResponse;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscountDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceNumber;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.serialization.ValueObjectJacksonModule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceResponseContractTest {

    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "contract-test");

    @RestController
    static class ContractController {
        @GetMapping("/contract/admin-invoice")
        AdminInvoiceResponse admin() {
            return AdminInvoiceResponse.of(new InvoiceView(scholarshipInvoice(), null, null, List.of()), List.of());
        }

        @GetMapping("/contract/me-invoice")
        MeInvoiceResponse me() {
            return MeInvoiceResponse.of(new InvoiceView(scholarshipInvoice(), null, null, List.of()), List.of());
        }
    }

    private static Invoice scholarshipInvoice() {
        InvoiceDraftHeader header = new InvoiceDraftHeader(1L, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT,
                InvoiceIssuingReason.MONTHLY_RUN, null);
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota 2026-03", YearMonth.of(2026, 3), null, null, null, null,
                1L, "CONTRACT:1", NonNegativeMoney.of(ars(100000)), null, null);
        InvoiceDiscountDraft discount = new InvoiceDiscountDraft(DiscountType.SCHOLARSHIP, "Beca 50 %", 7L,
                NonNegativeMoney.of(ars(100000)), NonNegativeMoney.of(ars(50000)), 1, 3);
        Invoice invoice = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header, line, Optional.of(discount), ACTOR);
        invoice.issueInvoice(InvoiceNumber.formatInvoiceNumber(InvoiceDocumentType.INVOICE, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10),
                new StudentSnapshot("Ana Ruiz", "30111222", null), ACTOR);
        return invoice;
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new ValueObjectJacksonModule())
            .registerModule(new JavaTimeModule());

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    @Tag("D-075")
    void anAdminInvoiceTravelsWithItsLineTheDiscountAndBothStatuses() throws Exception {
        mockMvc.perform(get("/contract/admin-invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("F-00000001"))
                .andExpect(jsonPath("$.invoiceType").value("MONTHLY_INSTALLMENT"))
                .andExpect(jsonPath("$.documentType").value("INVOICE"))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.linesTotalAmount.amount").value("100000.00"))
                .andExpect(jsonPath("$.discountsTotalAmount.amount").value("50000.00"))
                .andExpect(jsonPath("$.totalAmount.amount").value("50000.00"))
                .andExpect(jsonPath("$.paidAmount.amount").value("0.00"))
                .andExpect(jsonPath("$.creditedAmount.amount").value("0.00"))
                .andExpect(jsonPath("$.outstandingAmount.amount").value("50000.00"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].lineNumber").value(1))
                .andExpect(jsonPath("$.lines[0].feeType").value("MONTHLY_INSTALLMENT"))
                .andExpect(jsonPath("$.lines[0].period").value("2026-03"))
                .andExpect(jsonPath("$.lines[0].amount.amount").value("100000.00"))
                .andExpect(jsonPath("$.lines[0].discount.discountName").value("Beca 50 %"))
                .andExpect(jsonPath("$.lines[0].discount.discountSource").value("SCHOLARSHIP"))
                .andExpect(jsonPath("$.lines[0].discount.discountAmount.amount").value("50000.00"))
                .andExpect(jsonPath("$.lines[0].discount.installmentNumber").value(1))
                .andExpect(jsonPath("$.lines[0].discount.installmentsTotal").value(3))
                .andExpect(jsonPath("$.payments").isArray())
                .andExpect(jsonPath("$.receipts").isArray());
    }

    @Test
    @Tag("D-075")
    void theOldInvoiceFieldsAreGoneFromTheStudentContract() throws Exception {
        mockMvc.perform(get("/contract/me-invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount.amount").value("50000.00"))
                .andExpect(jsonPath("$.lines[0].discount.discountName").value("Beca 50 %"))
                .andExpect(jsonPath("$.amountDue").doesNotExist())
                .andExpect(jsonPath("$.remainingBalance").doesNotExist())
                .andExpect(jsonPath("$.baseAmount").doesNotExist())
                .andExpect(jsonPath("$.adjustments").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.feeType").doesNotExist())
                .andExpect(jsonPath("$.cancelledByName").doesNotExist());
    }
}
