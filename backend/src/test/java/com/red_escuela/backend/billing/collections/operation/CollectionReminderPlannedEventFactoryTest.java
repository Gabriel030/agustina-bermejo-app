package com.red_escuela.backend.billing.collections.operation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.bothChannels;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.installment;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.lateFeeNote;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.schedule;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderPlannedEventFactoryTest {

    private static DailyReminderDigest digestOf1203() {
        ResolvedReminderSchedule schedule = schedule(bothChannels("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1));
        Map<Currency, NonNegativeMoney> credit = Map.of(Currency.ARS, NonNegativeMoney.of(new BigDecimal("30000.00"), Currency.ARS));
        ReminderStudentFacts student = new ReminderStudentFacts(7L, List.of(installment(1L, "2027-03-11"), lateFeeNote(2L, 1L, "2027-03-11")), credit);
        return DailyReminderDigestPlanner.planDailyDigestOf(student, Map.of(1L, schedule), LocalDate.parse("2027-03-12"), NonBusinessDays.weekendsOnly(),
                ReminderDigestMode.DAILY_DIGEST);
    }

    @Test
    @Tag("D-353")
    @Tag("D-409")
    void theEventCarriesTheDigestAsValues() {
        CollectionReminderPlannedEvent event = CollectionReminderPlannedEventFactory.plannedEventOf(55L, 70L, digestOf1203());

        assertThat(event.reminderId()).isEqualTo(55L);
        assertThat(event.studentPersonId()).isEqualTo(70L);
        assertThat(event.businessDate()).isEqualTo(LocalDate.parse("2027-03-12"));
        assertThat(event.inAppMessages()).hasSize(1);
        assertThat(event.emailMessages()).hasSize(1);
        PlannedReminderMessage email = event.emailMessages().get(0);
        assertThat(email.format()).isEqualTo(PlannedReminderFormat.DAILY_DIGEST);
        assertThat(email.leadNoticeType()).isEqualTo(PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        assertThat(email.priority()).isEqualTo(4);
        assertThat(email.precedesDueDate()).isFalse();
        PlannedReminderBlock block = email.blocks().get(0);
        assertThat(block.currency()).isEqualTo(Currency.ARS);
        assertThat(block.total().amount()).isEqualByComparingTo(new BigDecimal("84000.00"));
        assertThat(block.availableCredit().amount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(block.shortfallInvoiceId()).isEqualTo(1L);
        assertThat(block.missingForNext().amount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        PlannedReminderLine installment = block.lines().get(0);
        assertThat(installment.invoiceKind()).isEqualTo(PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT);
        assertThat(installment.isHighlighted()).isTrue();
        assertThat(installment.scheduleVersionId()).isEqualTo(10L);
        assertThat(installment.exceptionVersionId()).isEqualTo(11L);
        assertThat(installment.groupVersionId()).isEqualTo(12L);
        assertThat(installment.globalSettingsVersionId()).isEqualTo(13L);
        PlannedReminderLine lateFee = block.lines().get(1);
        assertThat(lateFee.invoiceKind()).isEqualTo(PlannedReminderInvoiceKind.LATE_FEE);
        assertThat(lateFee.debitNote()).isTrue();
        assertThat(lateFee.originInvoiceId()).isEqualTo(1L);
        assertThat(lateFee.isHighlighted()).isFalse();
    }

    @Test
    @Tag("D-372")
    void aPreDueDigestIsFlaggedAsPrecedingTheDueDate() {
        ResolvedReminderSchedule schedule = schedule(bothChannels("before", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1));
        ReminderStudentFacts student = new ReminderStudentFacts(7L, List.of(installment(1L, "2027-03-11")), Map.of());
        DailyReminderDigest digest = DailyReminderDigestPlanner.planDailyDigestOf(student, Map.of(1L, schedule), LocalDate.parse("2027-03-10"), NonBusinessDays.weekendsOnly(),
                ReminderDigestMode.DAILY_DIGEST);

        CollectionReminderPlannedEvent event = CollectionReminderPlannedEventFactory.plannedEventOf(55L, 70L, digest);

        assertThat(event.emailMessages().get(0).precedesDueDate()).isTrue();
        assertThat(event.emailMessages().get(0).format()).isEqualTo(PlannedReminderFormat.OWN_NOTICE);
        assertThat(event.emailMessages().get(0).blocks().get(0).lines().get(0).precedesDueDate()).isTrue();
    }

    @Test
    void sourceAndEventEnumsHaveTheSameConstantsInTheSameOrder() {
        assertThat(java.util.Arrays.stream(ReminderMessageType.values()).map(Enum::name)).containsExactly(
                java.util.Arrays.stream(PlannedReminderNoticeType.values()).map(Enum::name).toArray(String[]::new));
        assertThat(java.util.Arrays.stream(com.red_escuela.backend.billing.collections.domain.ReminderMessageFormat.values()).map(Enum::name)).containsExactly(
                java.util.Arrays.stream(PlannedReminderFormat.values()).map(Enum::name).toArray(String[]::new));
        assertThat(java.util.Arrays.stream(PlannedReminderInvoiceKind.values()).map(Enum::name))
                .isSubsetOf(java.util.Arrays.stream(com.red_escuela.backend.billing.invoice.domain.InvoiceType.values()).map(Enum::name).toList());
    }

    @Test
    void theEventSurvivesAJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        CollectionReminderPlannedEvent event = CollectionReminderPlannedEventFactory.plannedEventOf(55L, 70L, digestOf1203());

        CollectionReminderPlannedEvent read = mapper.readValue(mapper.writeValueAsString(event), CollectionReminderPlannedEvent.class);

        assertThat(read).isEqualTo(event);
    }
}
