package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankTransferReceiptReviewEndpointIT extends AbstractBankTransferReceiptHttpIT {

    @Autowired
    UploadPaymentReceiptUseCase legacyUpload;

    private BankTransferReceiptSubmissionResult pendingReceipt(Debtor debtor, Long invoiceId, String operationNumber) {
        return submit(command(debtor, List.of(invoiceId), "80000.00", TODAY, operationNumber));
    }

    private MockHttpServletRequestBuilder review(String action, Long receiptId, String token, String json) {
        MockHttpServletRequestBuilder request = post("/bank-transfer-receipts/{id}/" + action, receiptId).header("Authorization", "Bearer " + token);
        return json == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String receiptStatusOf(Long receiptId) {
        return jdbc.queryForObject("select status from bank_transfer_receipt where id = ?", String.class, receiptId);
    }

    private String codeOf(MvcResult result) throws Exception {
        return bodyOf(result).get("code").asText();
    }

    @ParameterizedTest
    @CsvSource({
            "ALUMNO,403",
            "PRECEPTOR,403",
            "DOCENTE,403",
            "ADMINISTRATIVO,200",
            "DIRECTOR,200"})
    @Tag("D-110")
    @Tag("D-206")
    @Tag("D-270")
    void onlyAdministrativeStaffAndDirectorApproveReceipts(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(review("approve", submitted.receiptId(), token, null)).andExpect(status().is(expectedStatus));

        assertThat(receiptStatusOf(submitted.receiptId())).isEqualTo(expectedStatus == 200 ? "APPROVED" : "PENDING_REVIEW");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo(expectedStatus == 200 ? "PAID" : "PAYMENT_UNDER_REVIEW");
    }

    @ParameterizedTest
    @CsvSource({
            "ALUMNO,403",
            "PRECEPTOR,403",
            "DOCENTE,403",
            "ADMINISTRATIVO,200",
            "DIRECTOR,200"})
    @Tag("D-110")
    @Tag("D-206")
    @Tag("D-270")
    void onlyAdministrativeStaffAndDirectorRejectReceipts(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(review("reject", submitted.receiptId(), token, "{\"rejectionReason\":\"No coincide\"}")).andExpect(status().is(expectedStatus));

        assertThat(receiptStatusOf(submitted.receiptId())).isEqualTo(expectedStatus == 200 ? "REJECTED" : "PENDING_REVIEW");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo(expectedStatus == 200 ? "PENDING" : "PAYMENT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-270")
    void staffApprovesAReceiptTheyUploadedThemselvesAndTheAuditKeepsBothPeople() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Person administrative = newStaffPerson();
        String token = tokenOf(administrative, "ADMINISTRATIVO");
        MvcResult uploaded = mockMvc.perform(staffSubmission(debtor, token, List.of(march), "80000.00").request()).andExpect(status().isCreated()).andReturn();
        Long receiptId = bodyOf(uploaded).get("receiptId").asLong();

        mockMvc.perform(review("approve", receiptId, token, null)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select uploaded_by_id from bank_transfer_receipt where id = ?", Long.class, receiptId)).isEqualTo(administrative.getId());
        assertThat(jdbc.queryForObject("select reviewed_by_id from bank_transfer_receipt where id = ?", Long.class, receiptId)).isEqualTo(administrative.getId());
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{}|PAYMENT_REJECTION_REASON_REQUIRED",
            "{\"rejectionReason\":\"\"}|PAYMENT_REJECTION_REASON_REQUIRED",
            "{\"rejectionReason\":\"   \"}|PAYMENT_REJECTION_REASON_REQUIRED"})
    @Tag("D-139")
    void rejectingWithoutAReasonIsABadRequestWithItsCodeAndChangesNothing(String json, String expectedCode) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());

        MvcResult result = mockMvc.perform(review("reject", submitted.receiptId(), staffToken("ADMINISTRATIVO"), json)).andExpect(status().isBadRequest()).andReturn();

        assertThat(codeOf(result)).isEqualTo(expectedCode);
        assertThat(receiptStatusOf(submitted.receiptId())).isEqualTo("PENDING_REVIEW");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-139")
    void rejectingWithNoBodyAtAllAlsoAnswersWithTheReasonCode() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());

        MvcResult result = mockMvc.perform(review("reject", submitted.receiptId(), staffToken("DIRECTOR"), null)).andExpect(status().isBadRequest()).andReturn();

        assertThat(codeOf(result)).isEqualTo("PAYMENT_REJECTION_REASON_REQUIRED");
    }

    @Test
    @Tag("D-139")
    void aReasonOf501CharactersIsABadRequestAndOneOf500IsStored() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());
        String token = staffToken("ADMINISTRATIVO");

        MvcResult tooLong = mockMvc.perform(review("reject", submitted.receiptId(), token, "{\"rejectionReason\":\"" + "m".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(tooLong)).isEqualTo("PAYMENT_REJECTION_REASON_TOO_LONG");
        assertThat(receiptStatusOf(submitted.receiptId())).isEqualTo("PENDING_REVIEW");

        MvcResult rejected = mockMvc.perform(review("reject", submitted.receiptId(), token, "{\"rejectionReason\":\"" + "m".repeat(500) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(bodyOf(rejected).get("rejectionCode").asText()).isEqualTo("REVIEWER_REJECTION");
        assertThat(jdbc.queryForObject("select length(rejection_reason) from bank_transfer_receipt where id = ?", Integer.class, submitted.receiptId())).isEqualTo(500);
        assertThat(jdbc.queryForObject("select length(rejection_reason) from payment where id = ?", Integer.class, submitted.paymentId())).isEqualTo(500);
    }

    @Test
    @Tag("D-139")
    @Tag("D-208")
    void approvingWithAVerifiedAmountAndNoteUsesThemAndAnEmptyBodyKeepsTheDeclaredAmount() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());

        mockMvc.perform(review("approve", submitted.receiptId(), staffToken("ADMINISTRATIVO"),
                "{\"verifiedAmount\":{\"amount\":30000.00,\"currency\":\"ARS\"},\"transferDate\":\"2027-03-09\",\"reviewNote\":\"Llegó menos\"}")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select approved_amount from payment where id = ?", BigDecimal.class, submitted.paymentId())).isEqualByComparingTo("30000.00");
        assertThat(jdbc.queryForObject("select review_note from payment where id = ?", String.class, submitted.paymentId())).isEqualTo("Llegó menos");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");

        Debtor other = debtor();
        Long otherMarch = invoiceOf(other, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult otherSubmitted = pendingReceipt(other, otherMarch, nextOperationNumber());
        mockMvc.perform(review("approve", otherSubmitted.receiptId(), staffToken("DIRECTOR"), "{}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select approved_amount from payment where id = ?", BigDecimal.class, otherSubmitted.paymentId())).isEqualByComparingTo("80000.00");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"verifiedAmount\":{\"amount\":0,\"currency\":\"ARS\"}}|VERIFIED_PAYMENT_AMOUNT_INVALID",
            "{\"verifiedAmount\":{\"amount\":-1,\"currency\":\"ARS\"}}|VERIFIED_PAYMENT_AMOUNT_INVALID",
            "{\"verifiedAmount\":{\"amount\":10000000000.00,\"currency\":\"ARS\"}}|VERIFIED_PAYMENT_AMOUNT_INVALID",
            "{\"verifiedAmount\":{\"amount\":80000,\"currency\":\"USD\"}}|VERIFIED_PAYMENT_CURRENCY_MISMATCH",
            "{\"transferDate\":\"2027-03-10\"}|PAYMENT_DATE_IN_FUTURE"})
    @Tag("D-139")
    @Tag("D-169")
    void invalidVerificationIsABadRequestWithItsCodeAndTheReceiptStaysPending(String json, String expectedCode) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());

        MvcResult result = mockMvc.perform(review("approve", submitted.receiptId(), staffToken("ADMINISTRATIVO"), json)).andExpect(status().isBadRequest()).andReturn();

        assertThat(codeOf(result)).isEqualTo(expectedCode);
        assertThat(receiptStatusOf(submitted.receiptId())).isEqualTo("PENDING_REVIEW");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-320")
    void aTransferDateDifferentFromTheUploadDateAnswers409WithItsCodeAndTheRejectionIsCommitted() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());

        MvcResult result = mockMvc.perform(review("approve", submitted.receiptId(), staffToken("ADMINISTRATIVO"), "{\"transferDate\":\"2027-03-08\"}"))
                .andExpect(status().isConflict()).andReturn();

        assertThat(codeOf(result)).isEqualTo("RECEIPT_TRANSFER_DATE_MISMATCH");
        assertThat(receiptStatusOf(submitted.receiptId())).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select rejection_code from bank_transfer_receipt where id = ?", String.class, submitted.receiptId())).isEqualTo("TRANSFER_DATE_MISMATCH");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-082")
    void reviewingAnAlreadyReviewedReceiptAnswers409WithACode() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = pendingReceipt(debtor, march, nextOperationNumber());
        String token = staffToken("ADMINISTRATIVO");
        mockMvc.perform(review("approve", submitted.receiptId(), token, null)).andExpect(status().isOk());

        MvcResult again = mockMvc.perform(review("approve", submitted.receiptId(), token, null)).andExpect(status().isConflict()).andReturn();
        MvcResult rejectAfter = mockMvc.perform(review("reject", submitted.receiptId(), token, "{\"rejectionReason\":\"Tarde\"}")).andExpect(status().isConflict()).andReturn();

        assertThat(codeOf(again)).isNotBlank();
        assertThat(codeOf(rejectAfter)).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class,
                submitted.paymentId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-139")
    void aLegacyReceiptWithoutPaymentAnswers400WhenVerificationFieldsArrive() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceipt uploaded = legacyUpload.upload(march, debtor.studentId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 legado".getBytes()));

        MvcResult result = mockMvc.perform(review("approve", uploaded.getId(), staffToken("ADMINISTRATIVO"), "{\"reviewNote\":\"Nota\"}"))
                .andExpect(status().isBadRequest()).andReturn();

        assertThat(codeOf(result)).isEqualTo("LEGACY_RECEIPT_HAS_NO_VERIFIABLE_PAYMENT");
        assertThat(receiptStatusOf(uploaded.getId())).isEqualTo("PENDING_REVIEW");
        mockMvc.perform(review("approve", uploaded.getId(), staffToken("ADMINISTRATIVO"), null)).andExpect(status().isOk());
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
    }
}
