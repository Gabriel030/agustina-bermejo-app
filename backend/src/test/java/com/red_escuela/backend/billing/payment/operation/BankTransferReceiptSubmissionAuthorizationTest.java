package com.red_escuela.backend.billing.payment.operation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankTransferReceiptSubmissionAuthorizationTest extends AbstractBankTransferReceiptHttpIT {

    @ParameterizedTest
    @CsvSource({
            "ALUMNO,201",
            "ADMINISTRATIVO,403",
            "DIRECTOR,403",
            "PRECEPTOR,403",
            "DOCENTE,403"})
    @Tag("D-270")
    void onlyTheStudentSubmitsThroughTheOwnEndpoint(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(new Submission(null, token, List.of(march), "80000.00").request()).andExpect(status().is(expectedStatus));

        assertThat(paymentStatusOfInvoice(march)).isEqualTo(expectedStatus == 201 ? "PAYMENT_UNDER_REVIEW" : "PENDING");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(expectedStatus == 201 ? 1L : 0L);
    }

    @ParameterizedTest
    @CsvSource({
            "ALUMNO,403",
            "ADMINISTRATIVO,201",
            "DIRECTOR,201",
            "PRECEPTOR,403",
            "DOCENTE,403"})
    @Tag("D-270")
    void onlyAdministrativeStaffAndDirectorSubmitOnBehalfOfAStudent(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(new Submission(debtor.studentId(), token, List.of(march), "80000.00").request()).andExpect(status().is(expectedStatus));

        assertThat(paymentStatusOfInvoice(march)).isEqualTo(expectedStatus == 201 ? "PAYMENT_UNDER_REVIEW" : "PENDING");
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(expectedStatus == 201 ? 1L : 0L);
    }

    @Test
    @Tag("D-270")
    void aStudentCannotSubmitForAnotherStudentThroughTheStaffEndpointNorUseTheirInvoices() throws Exception {
        Debtor student = debtor();
        Debtor other = debtor();
        Long otherInvoice = invoiceOf(other, "80000.00", MARCH_DUE);

        mockMvc.perform(new Submission(other.studentId(), studentTokenOf(student), List.of(otherInvoice), "80000.00").request()).andExpect(status().isForbidden());
        mockMvc.perform(new Submission(null, studentTokenOf(student), List.of(otherInvoice), "80000.00").request()).andExpect(status().isNotFound());

        assertThat(paymentStatusOfInvoice(otherInvoice)).isEqualTo("PENDING");
        assertThat(paymentsOf(other.accountId())).isZero();
    }

    @Test
    @Tag("D-270")
    void withoutATokenNothingIsSubmitted() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);

        mockMvc.perform(new Submission(null, null, List.of(march), "80000.00").request()).andExpect(status().isForbidden());
        mockMvc.perform(new Submission(debtor.studentId(), null, List.of(march), "80000.00").request()).andExpect(status().isForbidden());

        assertThat(paymentsOf(debtor.accountId())).isZero();
    }
}
