package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.service.OverdueReceiptReviewAlerter;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankTransferReceiptReviewQueueIT extends AbstractBankTransferReceiptHttpIT {

    static final LocalDate MONDAY = LocalDate.of(2027, 3, 15);
    static final LocalDate THURSDAY = MONDAY.plusDays(3);
    static final LocalDate LATER_DUE = LocalDate.of(2027, 5, 31);

    @Autowired
    UploadPaymentReceiptUseCase legacyUpload;
    @Autowired
    OverdueReceiptReviewAlerter alerter;

    final List<LocalDate> insertedHolidays = new ArrayList<>();

    @AfterEach
    void removeHolidays() {
        insertedHolidays.forEach(date -> jdbc.update("delete from holiday where date = ?", date));
        insertedHolidays.clear();
    }

    private void clockAt(LocalDate date, int hour) {
        com.red_escuela.backend.shared.time.MutableClock.INSTANCE.setDateTime(date.atTime(hour, 0));
    }

    private static BigDecimal amountOf(JsonNode money) {
        return new BigDecimal(money.get("amount").asText());
    }

    private JsonNode queueEntryOf(Long receiptId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/bank-transfer-receipts").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return StreamSupport.stream(bodyOf(result).spliterator(), false).filter(node -> node.get("id").asLong() == receiptId).findFirst().orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({
            "ALUMNO,403",
            "PRECEPTOR,403",
            "DOCENTE,403",
            "ADMINISTRATIVO,200",
            "DIRECTOR,200"})
    @Tag("D-110")
    @Tag("D-270")
    void onlyAdministrativeStaffAndDirectorSeeTheReviewQueue(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(get("/bank-transfer-receipts").header("Authorization", "Bearer " + token)).andExpect(status().is(expectedStatus));
    }

    @Test
    @Tag("D-268")
    @Tag("D-139")
    void queueEntryShowsDeclaredDataInvoiceDetailAgeAndAlertState() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "50000.00", LATER_DUE);
        Long april = invoiceOf(debtor, "30000.00", LATER_DUE.plusMonths(1));
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(march, april), "80000.00", MONDAY, "OP-QUEUE-1"));
        String token = staffToken("DIRECTOR");

        clockAt(THURSDAY, 8);
        JsonNode beforeAlert = queueEntryOf(submitted.receiptId(), token);

        assertThat(amountOf(beforeAlert.get("declaredAmount"))).isEqualByComparingTo("80000.00");
        assertThat(beforeAlert.get("declaredAmount").get("currency").asText()).isEqualTo("ARS");
        assertThat(beforeAlert.get("transferDate").asText()).isEqualTo("2027-03-15");
        assertThat(beforeAlert.get("operationNumber").asText()).isEqualTo("OP-QUEUE-1");
        assertThat(beforeAlert.get("originAccount").asText()).isEqualTo("0170099220000067797370");
        assertThat(beforeAlert.get("holderName").asText()).isEqualTo("Ana Pérez");
        assertThat(beforeAlert.get("invoices")).hasSize(2);
        assertThat(beforeAlert.get("invoices").get(0).get("invoiceId").asLong()).isEqualTo(march);
        assertThat(amountOf(beforeAlert.get("invoices").get(0).get("totalAmount"))).isEqualByComparingTo("50000.00");
        assertThat(amountOf(beforeAlert.get("invoices").get(0).get("outstandingAtSubmission"))).isEqualByComparingTo("50000.00");
        assertThat(beforeAlert.get("invoices").get(1).get("invoiceId").asLong()).isEqualTo(april);
        assertThat(amountOf(beforeAlert.get("invoices").get(1).get("totalAmount"))).isEqualByComparingTo("30000.00");
        assertThat(beforeAlert.get("pendingBusinessDays").asInt()).isEqualTo(3);
        assertThat(beforeAlert.get("reviewAlertSent").asBoolean()).isFalse();
        assertThat(beforeAlert.get("reviewAlertSentAt").isNull()).isTrue();

        clockAt(THURSDAY, 9);
        alerter.alertOverdueReviews();
        JsonNode afterAlert = queueEntryOf(submitted.receiptId(), token);

        assertThat(afterAlert.get("reviewAlertSent").asBoolean()).isTrue();
        assertThat(afterAlert.get("reviewAlertSentAt").asText()).startsWith("2027-03-18T09:00");
    }

    @Test
    @Tag("D-268")
    @Tag("D-321")
    void ageInTheQueueSkipsWeekendsAndHolidays() throws Exception {
        jdbc.update("insert into holiday (date, description) values (?, 'test holiday')", MONDAY.plusDays(1));
        insertedHolidays.add(MONDAY.plusDays(1));
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(invoice), "80000.00", MONDAY, "OP-QUEUE-2"));
        String token = staffToken("ADMINISTRATIVO");

        clockAt(MONDAY.plusDays(7), 8);

        assertThat(queueEntryOf(submitted.receiptId(), token).get("pendingBusinessDays").asInt()).isEqualTo(4);
    }

    @Test
    @Tag("D-268")
    @Tag("D-260")
    void aLegacyReceiptWithoutPaymentShowsItsSingleInvoiceAndNoDeclaredData() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        BankTransferReceipt legacy = legacyUpload.upload(invoice, debtor.studentId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 legado".getBytes()));

        clockAt(THURSDAY, 8);
        JsonNode entry = queueEntryOf(legacy.getId(), staffToken("DIRECTOR"));

        assertThat(entry.get("declaredAmount").isNull()).isTrue();
        assertThat(entry.get("operationNumber").isNull()).isTrue();
        assertThat(entry.get("invoices")).hasSize(1);
        assertThat(entry.get("invoices").get(0).get("invoiceId").asLong()).isEqualTo(invoice);
        assertThat(amountOf(entry.get("invoices").get(0).get("totalAmount"))).isEqualByComparingTo("80000.00");
        assertThat(entry.get("invoices").get(0).get("outstandingAtSubmission").isNull()).isTrue();
        assertThat(entry.get("pendingBusinessDays").asInt()).isEqualTo(3);
    }

    @Test
    @Tag("D-268")
    @Tag("D-270")
    void aDecidedReceiptShowsNoPendingAgeButKeepsItsDeclaredData() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(invoice), "80000.00", MONDAY, "OP-QUEUE-3"));

        MvcResult result = mockMvc.perform(post("/bank-transfer-receipts/{id}/reject", submitted.receiptId()).header("Authorization", "Bearer " + staffToken("DIRECTOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rejectionReason\":\"No coincide\"}")).andExpect(status().isOk()).andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("status").asText()).isEqualTo("REJECTED");
        assertThat(body.get("pendingBusinessDays").isNull()).isTrue();
        assertThat(body.get("operationNumber").asText()).isEqualTo("OP-QUEUE-3");
        assertThat(body.get("invoices")).hasSize(1);
    }

    @Test
    @Tag("D-268")
    @Tag("D-110")
    void theStudentsOwnListShowsTheirDeclaredDataAndHidesTheReviewerFields() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(invoice), "80000.00", MONDAY, "OP-QUEUE-4"));

        MvcResult result = mockMvc.perform(get("/me/bank-transfer-receipts").header("Authorization", "Bearer " + studentTokenOf(debtor))).andExpect(status().isOk()).andReturn();

        JsonNode entry = StreamSupport.stream(bodyOf(result).spliterator(), false).filter(node -> node.get("id").asLong() == submitted.receiptId()).findFirst().orElseThrow();
        assertThat(entry.get("reviewAlertSent").isNull()).isTrue();
        assertThat(entry.get("pendingBusinessDays").isNull()).isTrue();
        assertThat(entry.get("originAccount").isNull()).isTrue();
        assertThat(entry.get("holderName").isNull()).isTrue();
        assertThat(entry.get("reviewAlertSentAt").isNull()).isTrue();
    }

    @Test
    @Tag("D-139")
    void theStudentsOwnListShowsDeclaredAmountOperationDateAndTheCoveredInstallments() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "50000.00", LATER_DUE);
        Long april = invoiceOf(debtor, "30000.00", LATER_DUE.plusMonths(1));
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(march, april), "80000.00", MONDAY, "OP-OWN-1"));

        JsonNode entry = ownEntryOf(submitted.receiptId(), studentTokenOf(debtor));

        assertThat(amountOf(entry.get("declaredAmount"))).isEqualByComparingTo("80000.00");
        assertThat(entry.get("transferDate").asText()).isEqualTo("2027-03-15");
        assertThat(entry.get("operationNumber").asText()).isEqualTo("OP-OWN-1");
        assertThat(entry.get("invoices")).hasSize(2);
        assertThat(entry.get("invoices").get(0).get("invoiceId").asLong()).isEqualTo(march);
        assertThat(amountOf(entry.get("invoices").get(0).get("totalAmount"))).isEqualByComparingTo("50000.00");
        assertThat(amountOf(entry.get("invoices").get(0).get("outstandingAtSubmission"))).isEqualByComparingTo("50000.00");
        assertThat(entry.get("invoices").get(1).get("invoiceId").asLong()).isEqualTo(april);
        assertThat(amountOf(entry.get("invoices").get(1).get("totalAmount"))).isEqualByComparingTo("30000.00");
    }

    @Test
    @Tag("D-139")
    void aStudentNeverSeesAnotherStudentsReceiptNorItsDeclaredData() throws Exception {
        clockAt(MONDAY, 10);
        Debtor owner = debtor();
        Debtor stranger = debtor();
        Long invoice = invoiceOf(owner, "80000.00", LATER_DUE);
        BankTransferReceiptSubmissionResult submitted = submit(command(owner, List.of(invoice), "80000.00", MONDAY, "OP-OWN-SECRET"));

        MvcResult result = mockMvc.perform(get("/me/bank-transfer-receipts").header("Authorization", "Bearer " + studentTokenOf(stranger))).andExpect(status().isOk()).andReturn();

        assertThat(bodyOf(result).toString()).doesNotContain("OP-OWN-SECRET");
        assertThat(StreamSupport.stream(bodyOf(result).spliterator(), false).anyMatch(node -> node.get("id").asLong() == submitted.receiptId())).isFalse();
    }

    @Test
    @Tag("D-139")
    void aLegacyReceiptInTheStudentsListKeepsItsSingleInvoiceAndNoDeclaredData() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        BankTransferReceipt legacy = legacyUpload.upload(invoice, debtor.studentId(), new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 legado propio".getBytes()));

        JsonNode entry = ownEntryOf(legacy.getId(), studentTokenOf(debtor));

        assertThat(entry.get("declaredAmount").isNull()).isTrue();
        assertThat(entry.get("operationNumber").isNull()).isTrue();
        assertThat(entry.get("invoices")).hasSize(1);
        assertThat(entry.get("invoices").get(0).get("invoiceId").asLong()).isEqualTo(invoice);
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRATIVO", "DIRECTOR"})
    @Tag("D-464")
    @Tag("D-270")
    void queueEntryUploadedByStaffShowsTheUploaderRoleAndNameAsInternalLoad(String role) throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        com.red_escuela.backend.identity.entity.Person staff = newStaffPerson();
        String staffToken = tokenOf(staff, role);
        MvcResult uploaded = mockMvc.perform(staffSubmission(debtor, staffToken, List.of(invoice), "80000.00").request()).andExpect(status().isCreated()).andReturn();

        JsonNode uploader = queueEntryOf(bodyOf(uploaded).get("receiptId").asLong(), staffToken).get("uploadedBy");

        assertThat(uploader.get("personId").asLong()).isEqualTo(staff.getId());
        assertThat(uploader.get("name").asText()).isEqualTo(staff.getName() + " " + staff.getLastname());
        assertThat(uploader.get("role").asText()).isEqualTo(role);
        assertThat(uploader.get("internal").asBoolean()).isTrue();
    }

    @Test
    @Tag("D-464")
    void queueEntryUploadedByTheStudentIsNotAnInternalLoad() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        MvcResult uploaded = mockMvc.perform(ownSubmission(debtor, List.of(invoice), "80000.00").request()).andExpect(status().isCreated()).andReturn();

        JsonNode uploader = queueEntryOf(bodyOf(uploaded).get("receiptId").asLong(), staffToken("ADMINISTRATIVO")).get("uploadedBy");

        assertThat(uploader.get("personId").asLong()).isEqualTo(personOf(debtor).getId());
        assertThat(uploader.get("role").asText()).isEqualTo("ALUMNO");
        assertThat(uploader.get("internal").asBoolean()).isFalse();
    }

    @Test
    @Tag("D-464")
    void aLegacyQueueEntryWithoutUploaderShowsNoUploadedBy() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        Long receiptId = legacyUpload.upload(invoice, debtor.studentId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 sin autor".getBytes())).getId();
        jdbc.update("update bank_transfer_receipt set uploaded_by_id = null where id = ?", receiptId);

        assertThat(queueEntryOf(receiptId, staffToken("DIRECTOR")).get("uploadedBy").isNull()).isTrue();
    }

    @Test
    @Tag("D-464")
    void theStudentsOwnListNeverExposesWhoUploaded() throws Exception {
        clockAt(MONDAY, 10);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        MvcResult uploaded = mockMvc.perform(staffSubmission(debtor, staffToken("ADMINISTRATIVO"), List.of(invoice), "80000.00").request()).andExpect(status().isCreated()).andReturn();

        JsonNode entry = ownEntryOf(bodyOf(uploaded).get("receiptId").asLong(), studentTokenOf(debtor));

        assertThat(entry.get("uploadedBy").isNull()).isTrue();
    }

    private JsonNode ownEntryOf(Long receiptId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/bank-transfer-receipts").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return StreamSupport.stream(bodyOf(result).spliterator(), false).filter(node -> node.get("id").asLong() == receiptId).findFirst().orElseThrow();
    }
}
