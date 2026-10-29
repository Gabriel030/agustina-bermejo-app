package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-441")
@Tag("D-110")
class AnnualUpfrontRequestViewIT extends AbstractAnnualUpfrontRequestIT {

    private JsonNode read(String path, String token, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private String tokenOf(Person person) {
        return transactionTemplate.execute(status -> jwtService.generateAccessToken(personRepository.findById(person.getId()).orElseThrow()));
    }

    private String staffToken() {
        return tokenOf(director);
    }

    private JsonNode requested() throws Exception {
        return objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());
    }

    @Test
    void theStudentSeesTheRequestWithItsLinesDiscountDeadlineAndTotal() throws Exception {
        publishAnnualPromotion();
        JsonNode created = requested();

        JsonNode detail = read("/me/annual-upfront-requests/" + created.get("id").asLong(), studentToken(), 200);

        assertThat(detail.get("status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(detail.get("invoiceId").asLong()).isEqualTo(created.get("invoiceId").asLong());
        assertThat(detail.get("programEnrollmentId").asLong()).isEqualTo(learner.programEnrollmentId());
        assertThat(detail.get("lectiveYear").asInt()).isEqualTo(2027);
        assertThat(detail.get("paymentDeadline").asText()).isEqualTo("2027-03-04T10:00:00");
        assertThat(new BigDecimal(detail.get("total").get("amount").asText())).isEqualByComparingTo("720000.00");
        assertThat(detail.get("lines")).hasSize(10);
        JsonNode first = detail.get("lines").get(0);
        assertThat(first.get("lineNumber").asInt()).isEqualTo(1);
        assertThat(first.get("period").toString()).contains("2027").contains("3");
        assertThat(new BigDecimal(first.get("baseAmount").get("amount").asText())).isEqualByComparingTo("80000.00");
        assertThat(new BigDecimal(first.get("discountAmount").get("amount").asText())).isEqualByComparingTo("8000.00");
        assertThat(new BigDecimal(first.get("finalAmount").get("amount").asText())).isEqualByComparingTo("72000.00");
        assertThat(detail.get("lines").get(9).get("lineNumber").asInt()).isEqualTo(10);
        assertThat(new BigDecimal(detail.get("discount").get("totalSaving").get("amount").asText())).isEqualByComparingTo("80000.00");
        assertThat(detail.get("discount").get("name").asText()).isNotBlank();
        assertThat(detail.get("acceptedTermsVersion").asText()).isEqualTo("v1");
        assertThat(detail.get("acceptedAt").asText()).isEqualTo("2027-03-02T10:00:00");
        assertThat(detail.get("paymentUnderReview").asBoolean()).isFalse();
        assertThat(detail.get("discardReason").isNull()).isTrue();
        assertThat(detail.get("decidedAt").isNull()).isTrue();
    }

    @Test
    void aRequestWithoutPromotionHasNoDiscountAndAFullPriceTotal() throws Exception {
        JsonNode detail = read("/me/annual-upfront-requests/" + requested().get("id").asLong(), studentToken(), 200);

        assertThat(detail.get("discount").isNull()).isTrue();
        assertThat(new BigDecimal(detail.get("total").get("amount").asText())).isEqualByComparingTo("800000.00");
        assertThat(new BigDecimal(detail.get("lines").get(0).get("discountAmount").get("amount").asText())).isEqualByComparingTo("0.00");
    }

    @Test
    void aRequestUnderReviewIsFlaggedAndADiscardedOneShowsItsReasonAndDecisionTime() throws Exception {
        Long reviewId = requested().get("id").asLong();
        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", reviewId);

        JsonNode underReview = read("/me/annual-upfront-requests/" + reviewId, studentToken(), 200);

        assertThat(underReview.get("status").asText()).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(underReview.get("paymentUnderReview").asBoolean()).isTrue();
        assertThat(underReview.get("discardReason").isNull()).isTrue();

        jdbc.update("update annual_upfront_request set status = 'DISCARDED', discard_reason = 'DEADLINE_EXPIRED', decided_at = ? where id = ?", java.time.LocalDateTime.of(2027, 3, 5, 9, 0), reviewId);

        JsonNode discarded = read("/me/annual-upfront-requests/" + reviewId, studentToken(), 200);

        assertThat(discarded.get("paymentUnderReview").asBoolean()).isFalse();
        assertThat(discarded.get("discardReason").asText()).isEqualTo("DEADLINE_EXPIRED");
        assertThat(discarded.get("decidedAt").asText()).isEqualTo("2027-03-05T09:00:00");
    }

    @Test
    void theStudentListsOnlyHisOwnRequestsNewestFirstAndEnrollmentListFiltersByEnrollment() throws Exception {
        IssuingScenarioFactory.Learner other = newCohortLearner();
        Long discardedId = requested().get("id").asLong();
        mockMvc.perform(post("/me/annual-upfront-requests/" + discardedId + "/discard").header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk());
        Long openId = requested().get("id").asLong();
        Long otherId = objectMapper.readTree(requestOf(other, true, "v1", java.util.UUID.randomUUID().toString(), studentTokenOf(other), 201).getResponse().getContentAsString()).get("id").asLong();

        JsonNode mine = read("/me/annual-upfront-requests", studentToken(), 200);
        JsonNode ofEnrollment = read("/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront-requests", studentToken(), 200);

        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).get("id").asLong()).isEqualTo(openId);
        assertThat(mine.get(1).get("id").asLong()).isEqualTo(discardedId);
        assertThat(ofEnrollment).hasSize(2);
        assertThat(ofEnrollment.get(0).get("id").asLong()).isEqualTo(openId);
        assertThat(read("/me/annual-upfront-requests", studentTokenOf(other), 200).get(0).get("id").asLong()).isEqualTo(otherId);
        read("/me/program-enrollments/" + other.programEnrollmentId() + "/annual-upfront-requests", studentToken(), 404);
    }

    @Test
    void aStudentCannotReadTheRequestOfAnotherStudentAndGetsTheSameNotFoundAsAnUnknownRequest() throws Exception {
        Long requestId = requested().get("id").asLong();
        String strangerToken = studentTokenOf(newCohortLearner());

        String foreign = mockMvc.perform(get("/me/annual-upfront-requests/" + requestId).header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get("/me/annual-upfront-requests/987654323").header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(foreign.replace(String.valueOf(requestId), "ID")).isEqualTo(unknown.replace("987654323", "ID"));
    }

    @Test
    void theStaffSeesTheRequestInTheDetailTheEnrollmentCardAndTheList() throws Exception {
        publishAnnualPromotion();
        JsonNode created = requested();
        Long requestId = created.get("id").asLong();

        JsonNode detail = read("/annual-upfront-requests/" + requestId, staffToken(), 200);
        JsonNode card = read("/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront-requests", staffToken(), 200);
        JsonNode list = read("/annual-upfront-requests", staffToken(), 200);

        assertThat(detail.get("id").asLong()).isEqualTo(requestId);
        assertThat(detail.get("lines")).hasSize(10);
        assertThat(new BigDecimal(detail.get("total").get("amount").asText())).isEqualByComparingTo("720000.00");
        assertThat(card).hasSize(1);
        assertThat(card.get(0).get("id").asLong()).isEqualTo(requestId);
        assertThat(list.get("items").get(0).get("id").asLong()).isEqualTo(requestId);
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);
        assertThat(list.get("page").asInt()).isZero();
        assertThat(list.get("size").asInt()).isEqualTo(20);
        read("/annual-upfront-requests/987654324", staffToken(), 404);
        read("/program-enrollments/987654325/annual-upfront-requests", staffToken(), 404);
    }

    @Test
    void theStaffListFiltersByStatusAndLectiveYearAndPaginates() throws Exception {
        IssuingScenarioFactory.Learner other = newCohortLearner();
        Long first = requested().get("id").asLong();
        Long second = objectMapper.readTree(requestOf(other, true, "v1", java.util.UUID.randomUUID().toString(), studentTokenOf(other), 201).getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", second);

        JsonNode awaiting = read("/annual-upfront-requests?status=AWAITING_PAYMENT", staffToken(), 200);
        JsonNode underReview = read("/annual-upfront-requests?status=PAYMENT_UNDER_REVIEW", staffToken(), 200);
        JsonNode rightYear = read("/annual-upfront-requests?lectiveYear=2027", staffToken(), 200);
        JsonNode wrongYear = read("/annual-upfront-requests?lectiveYear=2031", staffToken(), 200);
        JsonNode pageOne = read("/annual-upfront-requests?page=1&size=1", staffToken(), 200);
        JsonNode oversized = read("/annual-upfront-requests?size=5000", staffToken(), 200);
        JsonNode negative = read("/annual-upfront-requests?page=-3&size=0", staffToken(), 200);

        assertThat(awaiting.get("items")).hasSize(1);
        assertThat(awaiting.get("items").get(0).get("id").asLong()).isEqualTo(first);
        assertThat(underReview.get("items")).hasSize(1);
        assertThat(underReview.get("items").get(0).get("id").asLong()).isEqualTo(second);
        assertThat(rightYear.get("totalElements").asLong()).isEqualTo(2);
        assertThat(rightYear.get("items").get(0).get("id").asLong()).isEqualTo(second);
        assertThat(wrongYear.get("totalElements").asLong()).isZero();
        assertThat(wrongYear.get("items")).isEmpty();
        assertThat(pageOne.get("items")).hasSize(1);
        assertThat(pageOne.get("items").get(0).get("id").asLong()).isEqualTo(first);
        assertThat(pageOne.get("totalElements").asLong()).isEqualTo(2);
        assertThat(oversized.get("size").asInt()).isEqualTo(100);
        assertThat(negative.get("page").asInt()).isZero();
        assertThat(negative.get("size").asInt()).isEqualTo(1);
    }

    @Test
    void onlyAdministrativeStaffAndDirectorReadStaffEndpointsAndPreceptorsTeachersAndStudentsAreForbidden() throws Exception {
        Long requestId = requested().get("id").asLong();
        String preceptor = tokenOf(persistPerson("av.preceptor", "PRECEPTOR"));
        String teacher = tokenOf(persistPerson("av.docente", "DOCENTE"));

        for (String path : new String[]{"/annual-upfront-requests", "/annual-upfront-requests/" + requestId, "/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront-requests"}) {
            read(path, preceptor, 403);
            read(path, teacher, 403);
            read(path, studentToken(), 403);
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
        }
        read("/me/annual-upfront-requests", staffToken(), 403);
    }

    @Test
    @Tag("D-443")
    void anotherStudentQuotingWhileAPlaceIsReservedDoesNotSeeThePromotionAndSeesItAgainAfterTheDiscard() throws Exception {
        publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withPercent("10").availableFrom(LocalDate.of(2027, 1, 1)).withQuota(1));
        IssuingScenarioFactory.Learner other = newCohortLearner();
        String otherToken = studentTokenOf(other);
        String quotePath = "/me/program-enrollments/" + other.programEnrollmentId() + "/annual-upfront/quote";
        Long holderId = requested().get("id").asLong();

        JsonNode whileReserved = read(quotePath, otherToken, 200).get("quote");

        assertThat(whileReserved.get("discount").isNull()).isTrue();
        assertThat(new BigDecimal(whileReserved.get("total").get("amount").asText())).isEqualByComparingTo("800000.00");

        mockMvc.perform(post("/me/annual-upfront-requests/" + holderId + "/discard").header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk());
        JsonNode afterDiscard = read(quotePath, otherToken, 200).get("quote");

        assertThat(afterDiscard.get("discount").get("name").asText()).isNotBlank();
        assertThat(new BigDecimal(afterDiscard.get("total").get("amount").asText())).isEqualByComparingTo("720000.00");
    }

    @Test
    @Tag("D-131")
    void anUnissuedEnrollmentFeeNeitherEntersTheAnnualInvoiceNorReceivesTheAnnualDiscount() throws Exception {
        publishAnnualPromotion();
        long enrollmentFeeInvoicesBefore = count("select count(*) from invoice_line where fee_type = 'ENROLLMENT_FEE' and program_enrollment_id = " + learner.programEnrollmentId());

        JsonNode detail = read("/me/annual-upfront-requests/" + requested().get("id").asLong(), studentToken(), 200);

        Long invoiceId = detail.get("invoiceId").asLong();
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type <> 'MONTHLY_INSTALLMENT'")).isZero();
        assertThat(count("select count(*) from invoice_line where fee_type = 'ENROLLMENT_FEE' and program_enrollment_id = " + learner.programEnrollmentId())).isEqualTo(enrollmentFeeInvoicesBefore);
        assertThat(count("select count(*) from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id = " + invoiceId + ")")).isEqualTo(10);
        assertThat(count("select count(*) from invoice_discount d join invoice_line l on l.id = d.invoice_line_id where l.fee_type = 'ENROLLMENT_FEE' and l.program_enrollment_id = "
                + learner.programEnrollmentId())).isZero();
    }

    @Test
    @Tag("D-171")
    void aFixedDiscountLargerThanTheMonthlyPriceIsCutToTheBaseAndTheEffectiveAmountIsRecorded() throws Exception {
        publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withFixedAmount("1000000.00", Currency.ARS).availableFrom(LocalDate.of(2027, 1, 1)));

        JsonNode detail = read("/me/annual-upfront-requests/" + requested().get("id").asLong(), studentToken(), 200);

        assertThat(detail.get("lines")).allSatisfy(line -> {
            assertThat(new BigDecimal(line.get("discountAmount").get("amount").asText())).isEqualByComparingTo("80000.00");
            assertThat(new BigDecimal(line.get("finalAmount").get("amount").asText())).isEqualByComparingTo("0.00");
        });
        assertThat(new BigDecimal(detail.get("total").get("amount").asText())).isEqualByComparingTo("0.00");
        assertThat(count("select count(*) from invoice_discount where discount_amount = 80000.00 and invoice_line_id in (select id from invoice_line where invoice_id = "
                + detail.get("invoiceId").asLong() + ")")).isEqualTo(10);
    }
}
