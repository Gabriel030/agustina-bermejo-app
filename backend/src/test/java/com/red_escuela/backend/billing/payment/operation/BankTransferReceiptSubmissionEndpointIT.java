package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankTransferReceiptSubmissionEndpointIT extends AbstractBankTransferReceiptHttpIT {

    @Test
    @Tag("D-153")
    @Tag("D-154")
    @Tag("D-139")
    @Tag("D-270")
    void studentSubmitsMultipartReceipt() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march, april), "160000.00").request()).andExpect(status().isCreated()).andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("receiptStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(body.get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(new java.math.BigDecimal(body.get("declaredAmount").get("amount").asText())).isEqualByComparingTo("160000.00");
        assertThat(body.get("uploadDate").asText()).isEqualTo("2027-03-09");
        assertThat(body.get("items")).hasSize(2);
        assertThat(body.get("items").get(0).get("invoiceId").asLong()).isEqualTo(march);
        assertThat(body.get("items").get(0).get("position").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("protectedFromOverdue").asBoolean()).isTrue();
        assertThat(body.get("items").get(1).get("invoiceId").asLong()).isEqualTo(april);
        Long paymentId = body.get("paymentId").asLong();
        Map<String, Object> receipt = jdbc.queryForMap("select invoice_id, payment_id, status, uploaded_by_id, file_path from bank_transfer_receipt where id = ?",
                body.get("receiptId").asLong());
        assertThat(receipt).containsEntry("invoice_id", march).containsEntry("payment_id", paymentId).containsEntry("status", "PENDING_REVIEW")
                .containsEntry("uploaded_by_id", personOf(debtor).getId());
        assertThat((String) receipt.get("file_path")).matches("students/" + debtor.studentId() + "/comprobantes_[0-9a-f-]{36}\\.pdf");
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("PENDING");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-270")
    void staffSubmitsOnBehalfOfTheStudentAndTheAuditKeepsWhoUploaded() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        com.red_escuela.backend.identity.entity.Person administrative = newStaffPerson();
        String token = tokenOf(administrative, "ADMINISTRATIVO");

        MvcResult result = mockMvc.perform(staffSubmission(debtor, token, List.of(march), "80000.00").request()).andExpect(status().isCreated()).andReturn();

        Long receiptId = bodyOf(result).get("receiptId").asLong();
        assertThat(jdbc.queryForObject("select uploaded_by_id from bank_transfer_receipt where id = ?", Long.class, receiptId)).isEqualTo(administrative.getId());
        assertThat(jdbc.queryForObject("select registered_by_person_id from payment where id = ?", Long.class, bodyOf(result).get("paymentId").asLong()))
                .isEqualTo(administrative.getId());
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1L);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @ParameterizedTest
    @CsvSource({
            "declaredAmount,BANK_TRANSFER_AMOUNT_INVALID",
            "transferDate,BANK_TRANSFER_DATE_REQUIRED",
            "operationNumber,BANK_TRANSFER_OPERATION_NUMBER_REQUIRED",
            "originAccount,BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED",
            "holderName,BANK_TRANSFER_HOLDER_NAME_REQUIRED",
            "file,BANK_TRANSFER_RECEIPT_FILE_REQUIRED",
            "invoiceIds,BANK_TRANSFER_INVOICES_REQUIRED"})
    @Tag("D-139")
    @Tag("D-267")
    void eachMissingFieldReturns400WithItsField(String missingField, String expectedViolation) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").without(missingField).request()).andExpect(status().isBadRequest()).andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("code").asText()).isEqualTo("BANK_TRANSFER_DETAILS_REQUIRED");
        assertThat(StreamSupport.stream(body.get("violations").spliterator(), false).map(violation -> violation.get("code").asText())).containsExactly(expectedViolation);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(paymentsOf(debtor.accountId())).isZero();
        assertThat(filesStoredFor(debtor.studentId())).isZero();
    }

    @Test
    @Tag("D-139")
    @Tag("D-320")
    void futureTransferDateReturns400AndAPastOneIsAcceptedAtUpload() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);

        MvcResult future = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withTransferDate("2027-03-10").request())
                .andExpect(status().isBadRequest()).andReturn();
        mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withTransferDate("2027-03-01").request()).andExpect(status().isCreated());

        assertThat(bodyOf(future).get("violations").get(0).get("code").asText()).isEqualTo("BANK_TRANSFER_DATE_IN_FUTURE");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-269")
    void duplicateReturns409() throws Exception {
        Debtor first = debtor();
        Debtor second = debtor();
        Long firstInvoice = invoiceOf(first, "80000.00", MARCH_DUE);
        Long secondInvoice = invoiceOf(second, "80000.00", MARCH_DUE);
        String operationNumber = nextOperationNumber();
        mockMvc.perform(ownSubmission(first, List.of(firstInvoice), "80000.00").withOperationNumber(operationNumber).request()).andExpect(status().isCreated());

        MvcResult duplicate = mockMvc.perform(ownSubmission(second, List.of(secondInvoice), "80000.00").withOperationNumber(operationNumber).request())
                .andExpect(status().isConflict()).andReturn();

        assertThat(bodyOf(duplicate).get("code").asText()).isEqualTo("POSSIBLE_DUPLICATE_BANK_TRANSFER");
        assertThat(filesStoredFor(second.studentId())).isZero();
        assertThat(paymentStatusOfInvoice(secondInvoice)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-097")
    void foreignInvoiceReturns404WithoutBody() throws Exception {
        Debtor student = debtor();
        Debtor other = debtor();
        Long ownInvoice = invoiceOf(student, "80000.00", MARCH_DUE);
        Long foreignInvoice = invoiceOf(other, "80000.00", MARCH_DUE);

        MvcResult foreign = mockMvc.perform(ownSubmission(student, List.of(foreignInvoice), "80000.00").request()).andExpect(status().isNotFound()).andReturn();
        MvcResult foreignWithAnotherCurrency = mockMvc.perform(ownSubmission(student, List.of(foreignInvoice), "80000.00").withCurrency("USD").request())
                .andExpect(status().isNotFound()).andReturn();
        MvcResult mixed = mockMvc.perform(ownSubmission(student, List.of(ownInvoice, foreignInvoice), "160000.00").request()).andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mockMvc.perform(ownSubmission(student, List.of(987654321L), "80000.00").request()).andExpect(status().isNotFound()).andReturn();

        assertThat(foreign.getResponse().getContentAsString()).isEmpty();
        assertThat(foreignWithAnotherCurrency.getResponse().getContentAsString()).isEmpty();
        assertThat(mixed.getResponse().getContentAsString()).isEmpty();
        assertThat(missing.getResponse().getContentAsString()).isEmpty();
        assertThat(paymentStatusOfInvoice(foreignInvoice)).isEqualTo("PENDING");
        assertThat(paymentStatusOfInvoice(ownInvoice)).isEqualTo("PENDING");
        assertThat(filesStoredFor(student.studentId())).isZero();
    }

    @Test
    @Tag("D-097")
    void aLegacyPendingReceiptOfAForeignInvoiceDoesNotChangeTheForeignInvoiceAnswer() throws Exception {
        Debtor student = debtor();
        Debtor other = debtor();
        Long foreignInvoice = invoiceOf(other, "80000.00", MARCH_DUE);
        insertLegacyReceipt(foreignInvoice, "PENDING_REVIEW");

        MvcResult result = mockMvc.perform(ownSubmission(student, List.of(foreignInvoice), "80000.00").request()).andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    @Tag("D-186")
    @Tag("D-260")
    void aLegacyPendingReceiptOnASelectedInvoiceReturns409AndChangesNothing() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        insertLegacyReceipt(april, "PENDING_REVIEW");

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march, april), "160000.00").request()).andExpect(status().isConflict()).andReturn();

        assertThat(bodyOf(result).get("code").asText()).isEqualTo("LEGACY_RECEIPT_PENDING_ON_INVOICE");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(paymentsOf(debtor.accountId())).isZero();
        assertThat(filesStoredFor(debtor.studentId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-186")
    @Tag("D-260")
    void aLegacyPendingReceiptOnAnOverdueSelectedInvoiceAlsoReturns409() throws Exception {
        Debtor debtor = debtor();
        Long overdue = invoiceOf(debtor, "80000.00", MARCH_DUE);
        jdbc.update("update invoice set payment_status = 'OVERDUE' where id = ?", overdue);
        insertLegacyReceipt(overdue, "PENDING_REVIEW");

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(overdue), "80000.00").request()).andExpect(status().isConflict()).andReturn();

        assertThat(bodyOf(result).get("code").asText()).isEqualTo("LEGACY_RECEIPT_PENDING_ON_INVOICE");
    }

    @Test
    @Tag("D-260")
    void legacyReceiptsThatAreNotPendingOrBelongToOtherInvoicesDoNotBlock() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        insertLegacyReceipt(march, "REJECTED");
        insertLegacyReceipt(april, "PENDING_REVIEW");

        mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").request()).andExpect(status().isCreated());

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-139")
    void replayDoesNotStoreASecondFile() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MockMultipartFile file = pdfFile("mismo-contenido");
        String key = nextKey();
        String operationNumber = nextOperationNumber();

        MvcResult first = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(file).withKey(key).withOperationNumber(operationNumber).request())
                .andExpect(status().isCreated()).andReturn();
        MvcResult replay = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(file).withKey(key).withOperationNumber(operationNumber).request())
                .andExpect(status().isCreated()).andReturn();

        assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-139")
    void theSameKeyWithAnotherFileConflictsWithoutStoringIt() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        String key = nextKey();
        String operationNumber = nextOperationNumber();
        mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(pdfFile("uno")).withKey(key).withOperationNumber(operationNumber).request())
                .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(pdfFile("dos")).withKey(key).withOperationNumber(operationNumber).request())
                .andExpect(status().isConflict()).andReturn();

        assertThat(bodyOf(conflict).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
    }

    @Test
    @Tag("D-139")
    void aMissingIdempotencyKeyReturns400() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").without("idempotencyKey").request()).andExpect(status().isBadRequest()).andReturn();

        assertThat(bodyOf(result).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThat(filesStoredFor(debtor.studentId())).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "application/pdf,MZ-este-es-un-ejecutable,BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH",
            "image/png,%PDF-1.4 contenido,BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH",
            "text/html,<html>hola</html>,BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED",
            "application/x-msdownload,MZ,BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED"})
    @Tag("D-267")
    void aFileWhoseContentDoesNotMatchAnAllowedTypeIsRejectedAndNeverStored(String declaredType, String content, String expectedViolation) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MockMultipartFile spoofed = new MockMultipartFile("file", "comprobante.pdf", declaredType, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(spoofed).request()).andExpect(status().isBadRequest()).andReturn();

        assertThat(bodyOf(result).get("violations").get(0).get("code").asText()).isEqualTo(expectedViolation);
        assertThat(filesStoredFor(debtor.studentId())).isZero();
        assertThat(paymentsOf(debtor.accountId())).isZero();
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-267")
    void anEmptyFileCountsAsMissing() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(new MockMultipartFile("file", "vacio.pdf", "application/pdf", new byte[0])).request())
                .andExpect(status().isBadRequest()).andReturn();

        assertThat(bodyOf(result).get("violations").get(0).get("code").asText()).isEqualTo("BANK_TRANSFER_RECEIPT_FILE_REQUIRED");
    }

    @Test
    @Tag("D-267")
    void aTraversalFileNameNeverChoosesTheStoredPath() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MockMultipartFile traversal = new MockMultipartFile("file", "../../../../etc/passwd\u0000.pdf", "application/pdf", pdfBytes("traversal"));

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(traversal).request()).andExpect(status().isCreated()).andReturn();

        String storedPath = jdbc.queryForObject("select file_path from bank_transfer_receipt where id = ?", String.class, bodyOf(result).get("receiptId").asLong());
        assertThat(storedPath).matches("students/" + debtor.studentId() + "/comprobantes_[0-9a-f-]{36}\\.pdf").doesNotContain("..").doesNotContain("passwd");
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(uploadsDirectory).resolve(storedPath))).isTrue();
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
    }

    @Test
    @Tag("D-139")
    void aPdfPngAndJpegAreAccepted() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);

        mockMvc.perform(ownSubmission(debtor, List.of(march), "80000.00").withFile(new MockMultipartFile("file", "a.png", "image/png", pngBytes("png"))).request())
                .andExpect(status().isCreated());
        mockMvc.perform(ownSubmission(debtor, List.of(april), "80000.00").withFile(new MockMultipartFile("file", "b.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).request()).andExpect(status().isCreated());

        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(2);
    }

    @Test
    @Tag("D-186")
    void anOverdueInvoiceAcceptsTheReceiptButStaysOverdueThroughHttp() throws Exception {
        Debtor debtor = debtor();
        Long overdue = invoiceOf(debtor, "80000.00", MARCH_DUE);
        jdbc.update("update invoice set payment_status = 'OVERDUE' where id = ?", overdue);

        MvcResult result = mockMvc.perform(ownSubmission(debtor, List.of(overdue), "80000.00").request()).andExpect(status().isCreated()).andReturn();

        assertThat(bodyOf(result).get("items").get(0).get("protectedFromOverdue").asBoolean()).isFalse();
        assertThat(paymentStatusOfInvoice(overdue)).isEqualTo("OVERDUE");
    }

    void insertLegacyReceipt(Long invoiceId, String status) {
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, ?, ?, ?, 0)", invoiceId,
                "students/legacy/comprobantes_" + System.nanoTime() + ".pdf", java.sql.Timestamp.valueOf(LocalDate.of(2027, 3, 8).atStartOfDay()), status);
    }
}
