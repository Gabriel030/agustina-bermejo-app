package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs four HTTP refund-listing scenarios and its time includes the one-time Spring context start-up")
class RefundListingIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    private long requestedRefund(Debtor debtor, String amount) throws Exception {
        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), amount)).andExpect(status().isCreated()).andReturn();
        return bodyOf(result).get("parts").get(0).get("id").asLong();
    }

    private int statusOf(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private JsonNode listOf(String path, String token) throws Exception {
        return bodyOf(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
    }

    private List<Long> idsOf(JsonNode refunds) {
        return refunds.findValues("id").stream().map(JsonNode::asLong).toList();
    }

    @Test
    @Tag("D-159")
    @Tag("D-216")
    void theStaffListsTheRefundsOfAStudentNewestRequestFirstWithEveryRefundField() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long first = requestedRefund(debtor, "10000.00");
        long second = requestedRefund(debtor, "20000.00");
        long third = requestedRefund(debtor, "30000.00");
        jdbc.update("update refund set requested_on = ? where id = ?", EARLY_MARCH.plusDays(10), first);
        jdbc.update("update refund set requested_on = ? where id = ?", EARLY_MARCH.plusDays(5), second);
        jdbc.update("update refund set requested_on = ? where id = ?", EARLY_MARCH.plusDays(5), third);

        JsonNode refunds = listOf("/students/" + debtor.studentId() + "/refunds", staffToken("ADMINISTRATIVO"));

        assertThat(idsOf(refunds)).containsExactly(first, third, second);
        JsonNode newestOfTheSameDay = refunds.get(1);
        assertThat(newestOfTheSameDay.get("status").asText()).isEqualTo("APPROVED");
        assertThat(newestOfTheSameDay.get("methodType").asText()).isEqualTo("CASH");
        assertThat(decimalOf(newestOfTheSameDay.get("amount"))).isEqualByComparingTo("30000.00");
        assertThat(newestOfTheSameDay.get("destinationAccount").asText()).isEqualTo(REQUEST_ACCOUNT);
        assertThat(newestOfTheSameDay.get("destinationHolder").asText()).isEqualTo(REQUEST_HOLDER);
        assertThat(newestOfTheSameDay.get("requestedOn").asText()).isEqualTo(EARLY_MARCH.plusDays(5).toString());
        assertThat(listOf("/students/" + debtor.studentId() + "/refunds", staffToken("DIRECTOR"))).hasSize(3);
    }

    @Test
    @Tag("D-159")
    void aStudentOnlySeesItsOwnRefundsAndNeverThoseOfAnotherStudent() throws Exception {
        Debtor own = debtor();
        Debtor other = debtor();
        cashPayment(own, "100000.00", EARLY_MARCH);
        cashPayment(other, "100000.00", EARLY_MARCH);
        long ownRefund = requestedRefund(own, "10000.00");
        long otherRefund = requestedRefund(other, "20000.00");

        JsonNode mine = listOf("/me/refunds", studentTokenOf(own));
        JsonNode theirs = listOf("/me/refunds", studentTokenOf(other));

        assertThat(idsOf(mine)).containsExactly(ownRefund);
        assertThat(idsOf(theirs)).containsExactly(otherRefund);
        assertThat(decimalOf(mine.get(0).get("amount"))).isEqualByComparingTo("10000.00");
    }

    @Test
    @Tag("D-159")
    void aStudentWithoutRefundsGetsAnEmptyListAndAnUnknownStudentIsNotFound() throws Exception {
        Debtor debtor = debtor();

        assertThat(listOf("/me/refunds", studentTokenOf(debtor))).isEmpty();
        assertThat(listOf("/students/" + debtor.studentId() + "/refunds", staffToken("DIRECTOR"))).isEmpty();
        assertThat(statusOf("/students/999999999/refunds", staffToken("DIRECTOR"))).isEqualTo(404);
    }

    @Test
    @Tag("D-216")
    void everyRoleOutsideTheAllowedOnesIsRejectedOnTheRefundListings() throws Exception {
        Debtor debtor = debtor();
        String studentListing = "/students/" + debtor.studentId() + "/refunds";

        assertThat(statusOf(studentListing, studentTokenOf(debtor))).isEqualTo(403);
        assertThat(statusOf(studentListing, staffToken("PRECEPTOR"))).isEqualTo(403);
        assertThat(statusOf(studentListing, staffToken("DOCENTE"))).isEqualTo(403);
        assertThat(statusOf("/me/refunds", staffToken("ADMINISTRATIVO"))).isEqualTo(403);
        assertThat(statusOf("/me/refunds", staffToken("DIRECTOR"))).isEqualTo(403);
        assertThat(statusOf("/me/refunds", staffToken("DOCENTE"))).isEqualTo(403);
        assertThat(mockMvc.perform(get(studentListing)).andReturn().getResponse().getStatus()).isIn(401, 403);
        assertThat(mockMvc.perform(get("/me/refunds")).andReturn().getResponse().getStatus()).isIn(401, 403);
    }
}
