package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.operation.AbstractStudentDiscountIssuingIT;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-156")
@Tag("D-130")
@Tag("D-302")
@Tag("D-152")
@Tag("D-292")
@Tag("D-131")
@Tag("D-295")
@Tag("D-440")
@Tag("D-110")
class AnnualUpfrontQuoteControllerIT extends AbstractStudentDiscountIssuingIT {

    private static final LocalDate DEADLINE = LocalDate.of(2027, 2, 28);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ConfigurationVersionRepository configurationVersions;
    @Autowired
    private DiscountDefinitionRepository definitionRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private StudentCurrentAccountService accountService;

    private final List<Long> createdDefinitionIds = new ArrayList<>();

    @BeforeEach
    void setUpAnnualUpfront() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.TEN));
        today(LocalDate.of(2027, 2, 10));
        Long termId = jdbc.queryForObject("select term_id from program_year_billing_template where id = ?", Long.class, templateId);
        transactionTemplate.executeWithoutResult(status -> configurationVersions.saveAndFlush(ConfigurationVersion.publish(
                ConfigurationTopic.ofAnnualUpfront(offering.programId(), termId), new AnnualUpfrontSettings(DEADLINE, 48), LocalDate.of(2000, 1, 1), "Fixture de prueba",
                director.getId(), LocalDate.now(), null, Instant.now())));
    }

    @AfterEach
    void removeAnnualUpfrontFixtures() {
        jdbc.execute("alter table discount_definition_status_change disable trigger trg_discount_definition_status_change_append_only");
        try {
            createdDefinitionIds.forEach(id -> {
                jdbc.update("delete from discount_definition_status_change where discount_definition_id = ?", id);
                jdbc.update("delete from discount_definition where id = ?", id);
            });
        } finally {
            jdbc.execute("alter table discount_definition_status_change enable trigger trg_discount_definition_status_change_append_only");
        }
    }

    private String studentToken(Long studentId) {
        return transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(studentId).orElseThrow().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            personRepository.save(person);
            return jwtService.generateAccessToken(person);
        });
    }

    private String tokenOf(Person person) {
        return transactionTemplate.execute(status -> jwtService.generateAccessToken(personRepository.findById(person.getId()).orElseThrow()));
    }

    private JsonNode studentQuote() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront/quote")
                .header("Authorization", "Bearer " + studentToken(learner.studentId()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode staffQuote() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront/quote")
                .header("Authorization", "Bearer " + tokenOf(administrative))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void publishAnnualPromotion(DiscountDraftBuilder draft) {
        DiscountDefinition saved = transactionTemplate.execute(status -> definitionRepository.saveAndFlush(
                DiscountDefinition.publishDiscountDefinition(draft.availableFrom(LocalDate.of(2027, 1, 1)).build(), director.getId(), Instant.now())));
        createdDefinitionIds.add(saved.getId());
    }

    private long countOfRows(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private Long seedOpenAnnualInvoice() {
        Long priceVersionId = jdbc.queryForObject("select max(id) from price_version", Long.class);
        return transactionTemplate.execute(status -> {
            Long accountId = accountService.findOrOpenAndLockStudentCurrentAccount(learner.studentId(), Currency.ARS).getId();
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT, InvoiceIssuingReason.MONTHLY_RUN, null);
            List<InvoiceLineDraft> lines = new ArrayList<>();
            for (int month = 3; month <= 12; month++) {
                lines.add(new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + month, YearMonth.of(2027, month), learner.programEnrollmentId(), null, null, null, priceVersionId,
                        "AN:" + System.nanoTime() + ":" + month, NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS)));
            }
            Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines, Map.of(), ACTOR);
            draft.submitDraftForPayment(LocalDateTime.of(2027, 2, 12, 10, 0), ACTOR);
            return invoiceRepository.saveAndFlush(draft).getId();
        });
    }

    @Test
    void withoutAnyPromotionTheStudentSeesTenInstallmentsAtTheFullPrice() throws Exception {
        JsonNode quote = studentQuote();

        assertThat(quote.get("eligible").asBoolean()).isTrue();
        assertThat(quote.get("lectiveYear").asInt()).isEqualTo(2027);
        assertThat(quote.get("requestDeadline").asText()).isEqualTo("2027-02-28");
        assertThat(quote.get("quote").get("lines")).hasSize(10);
        assertThat(quote.get("quote").get("lines").get(0).get("period").asText()).isEqualTo("2027-03");
        assertThat(quote.get("quote").get("lines").get(9).get("period").asText()).isEqualTo("2027-12");
        assertThat(new BigDecimal(quote.get("quote").get("total").get("amount").asText())).isEqualByComparingTo("800000.00");
        assertThat(quote.get("quote").get("discount").isNull()).isTrue();
        assertThat(quote.get("quote").get("paymentDeadline").asText()).startsWith("2027-02-12T");
    }

    @Test
    void anAnnualPromotionOfTenPercentLowersEveryLineAndTheTotal() throws Exception {
        publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withPercent("10"));

        JsonNode quote = studentQuote().get("quote");

        assertThat(quote.get("lines")).allSatisfy(line -> assertThat(new BigDecimal(line.get("discountAmount").get("amount").asText())).isEqualByComparingTo("8000.00"));
        assertThat(new BigDecimal(quote.get("total").get("amount").asText())).isEqualByComparingTo("720000.00");
        assertThat(new BigDecimal(quote.get("discount").get("savingPerLine").get("amount").asText())).isEqualByComparingTo("8000.00");
    }

    @Test
    void theStaffSeesTheSameQuoteAsTheStudent() throws Exception {
        publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withPercent("10"));

        assertThat(staffQuote().get("quote").get("total")).isEqualTo(studentQuote().get("quote").get("total"));
    }

    @Test
    void theRequestWindowClosesTheDayAfterTheDeadline() throws Exception {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 27, 12, 0));
        assertThat(studentQuote().get("eligible").asBoolean()).isTrue();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 28, 23, 59));
        assertThat(studentQuote().get("eligible").asBoolean()).isTrue();

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 1, 0, 0));
        JsonNode closed = studentQuote();

        assertThat(closed.get("eligible").asBoolean()).isFalse();
        assertThat(closed.get("reasonCode").asText()).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
        assertThat(closed.get("quote").isNull()).isTrue();
    }

    @Test
    void anActiveScholarshipBlocksTheYearAndARevokedOneDoesNot() throws Exception {
        Long scholarshipId = factory.grantScholarship(offering, learner, Percentage.ofPoints(new BigDecimal("50")), YearMonth.of(2027, 3), YearMonth.of(2027, 12));

        JsonNode blocked = studentQuote();

        assertThat(blocked.get("eligible").asBoolean()).isFalse();
        assertThat(blocked.get("reasonCode").asText()).isEqualTo("ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP");
        jdbc.update("update student_discount set status = 'REVOKED', revoked_at = now(), revoked_by_id = ?, revocation_reason = 'Fin de la beca', revoked_from_period = '2027-03-01' where id = ?",
                director.getId(), scholarshipId);
        assertThat(studentQuote().get("eligible").asBoolean()).isTrue();
    }

    @Test
    void anOverdueInvoiceBlocksTheYearUntilItIsPaid() throws Exception {
        Long invoiceId = issue(adHocChargeOf(FeeType.ADMINISTRATIVE_FEE, YearMonth.of(2027, 2), "1000.00", "overdue-" + System.nanoTime()));
        today(LocalDate.of(2027, 2, 20));
        assertThat(studentQuote().get("eligible").asBoolean()).isTrue();

        today(LocalDate.of(2027, 2, 21));
        JsonNode blocked = studentQuote();
        assertThat(blocked.get("reasonCode").asText()).isEqualTo("OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT");

        jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
        assertThat(studentQuote().get("eligible").asBoolean()).isTrue();
    }

    @Test
    void anOpenAnnualInvoiceIsReportedAsTheExistingRequest() throws Exception {
        Long annualInvoiceId = seedOpenAnnualInvoice();

        JsonNode quote = studentQuote();

        assertThat(quote.get("eligible").asBoolean()).isFalse();
        assertThat(quote.get("reasonCode").asText()).isEqualTo("ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED");
        assertThat(quote.get("existingRequestInvoiceId").asLong()).isEqualTo(annualInvoiceId);
    }

    @Test
    void anIssuedMonthlyInstallmentOfTheYearBlocksTheRequest() throws Exception {
        issueInstallment(YearMonth.of(2027, 3));
        today(LocalDate.of(2027, 3, 1));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 20, 12, 0));

        JsonNode quote = studentQuote();

        assertThat(quote.get("reasonCode").asText()).isEqualTo("NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT");
    }

    @Test
    void repeatedQuotesWriteNothing() throws Exception {
        publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withPercent("10"));
        List<String> tables = List.of("invoice", "invoice_line", "invoice_discount", "student_discount", "current_account_entry", "student_current_account", "payment");
        List<Long> before = tables.stream().map(this::countOfRows).toList();

        studentQuote();
        staffQuote();
        studentQuote();

        assertThat(tables.stream().map(this::countOfRows).toList()).isEqualTo(before);
    }

    @Test
    void onlyTheOwnerStudentAndTheAdministrativeStaffCanQuote() throws Exception {
        String path = "/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront/quote";
        String staffPath = "/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront/quote";
        IssuingScenarioFactory.Learner other = newPlainLearner();

        mockMvc.perform(get(path).header("Authorization", "Bearer " + studentToken(other.studentId()))).andExpect(status().isNotFound());
        mockMvc.perform(get(staffPath).header("Authorization", "Bearer " + studentToken(learner.studentId()))).andExpect(status().isForbidden());
        mockMvc.perform(get(staffPath).header("Authorization", "Bearer " + tokenOf(persistPerson("aq.preceptor", "PRECEPTOR")))).andExpect(status().isForbidden());
        mockMvc.perform(get(staffPath).header("Authorization", "Bearer " + tokenOf(persistPerson("aq.docente", "DOCENTE")))).andExpect(status().isForbidden());
        mockMvc.perform(get(staffPath).header("Authorization", "Bearer " + tokenOf(director))).andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isForbidden());
    }
}
