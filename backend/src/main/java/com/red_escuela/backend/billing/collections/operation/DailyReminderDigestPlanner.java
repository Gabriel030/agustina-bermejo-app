package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderConfigurationVersions;
import com.red_escuela.backend.billing.collections.domain.ReminderDebtEntry;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestAssembler;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderDueNotice;
import com.red_escuela.backend.billing.collections.domain.ReminderImputationOrder;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderOccurrence;
import com.red_escuela.backend.billing.collections.domain.ReminderOccurrenceExpander;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.collections.domain.SuppressedReminder;
import com.red_escuela.backend.billing.collections.domain.ReminderPriorDebt;
import com.red_escuela.backend.billing.collections.policy.NewInstallmentWaitsForPriorDebtPolicy;
import com.red_escuela.backend.billing.collections.policy.PendingCancellationSuppressesReminderPolicy;
import com.red_escuela.backend.billing.collections.policy.PriorDebtReminderContext;
import com.red_escuela.backend.billing.collections.policy.ReceiptUnderReviewSuppressesReminderPolicy;
import com.red_escuela.backend.billing.collections.policy.ReminderEligibleInvoicePolicy;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DailyReminderDigestPlanner {

    private static final ReminderEligibleInvoicePolicy ELIGIBLE_INVOICE = new ReminderEligibleInvoicePolicy();
    private static final ReceiptUnderReviewSuppressesReminderPolicy RECEIPT_UNDER_REVIEW = new ReceiptUnderReviewSuppressesReminderPolicy();
    private static final PendingCancellationSuppressesReminderPolicy PENDING_CANCELLATION = new PendingCancellationSuppressesReminderPolicy();
    private static final NewInstallmentWaitsForPriorDebtPolicy WAITS_FOR_PRIOR_DEBT = new NewInstallmentWaitsForPriorDebtPolicy();
    private static final Set<InvoicePaymentStatus> OPEN_PAYMENT_STATUSES = Set.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE,
            InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);

    private static final int EMAIL_SHIFT_LOOKAHEAD_DAYS = 14;

    private DailyReminderDigestPlanner() {
    }

    public static DailyReminderDigest planDailyDigestOf(ReminderStudentFacts student, Map<Long, ResolvedReminderSchedule> schedulesByInvoiceId, LocalDate businessDate,
                                                        NonBusinessDays nonBusinessDays, ReminderDigestMode mode) {
        List<ReminderInvoiceFact> listed = listedDebtOf(student);
        List<ReminderDueNotice> inAppNotices = new ArrayList<>();
        List<ReminderDueNotice> emailNotices = new ArrayList<>();
        List<SuppressedReminder> suppressed = new ArrayList<>();
        LocalDate horizon = businessDate.plusDays(EMAIL_SHIFT_LOOKAHEAD_DAYS);
        for (ReminderInvoiceFact fact : student.invoices()) {
            PolicyVerdict receipt = RECEIPT_UNDER_REVIEW.evaluateRule(fact);
            PolicyVerdict cancellation = PENDING_CANCELLATION.evaluateRule(fact);
            if (!isRemindable(fact)) {
                continue;
            }
            Optional<PolicyVerdict> denial = firstDenial(receipt, cancellation);
            Optional<ResolvedReminderSchedule> schedule = Optional.ofNullable(schedulesByInvoiceId.get(fact.invoiceId()));
            if (denial.isPresent() && schedule.isPresent()) {
                for (ReminderOccurrence occurrence : ReminderOccurrenceExpander.expandOccurrencesUpTo(fact, schedule.get().notices(), horizon)) {
                    collectSuppressed(occurrence, businessDate, nonBusinessDays, denial.get().code(), suppressed);
                }
            }
            if (denial.isEmpty()) {
                ResolvedReminderSchedule resolved = schedule.orElseThrow(() -> new IllegalArgumentException("The invoice " + fact.invoiceId() + " has no resolved reminder schedule"));
                for (ReminderOccurrence occurrence : ReminderOccurrenceExpander.expandOccurrencesUpTo(fact, resolved.notices(), horizon)) {
                    collectDue(fact, occurrence, listed, businessDate, nonBusinessDays, inAppNotices, emailNotices, suppressed);
                }
            }
        }
        List<ReminderDebtEntry> debt = ReminderImputationOrder.sortMostOverdueFirst(listed).stream()
                .map(fact -> debtEntryOf(fact, schedulesByInvoiceId.get(fact.invoiceId())))
                .toList();
        return ReminderDigestAssembler.assembleDigestOf(student.studentId(), businessDate, debt, inAppNotices, emailNotices, student.creditByCurrency(), mode, suppressed);
    }

    private static List<ReminderInvoiceFact> listedDebtOf(ReminderStudentFacts student) {
        return student.invoices().stream()
                .filter(fact -> (isRemindable(fact) || isOpenLateFeeDebitNote(fact)) && PENDING_CANCELLATION.evaluateRule(fact).allowed())
                .toList();
    }

    private static boolean isRemindable(ReminderInvoiceFact fact) {
        return ELIGIBLE_INVOICE.evaluateRule(withoutPaymentReview(fact)).allowed();
    }

    private static ReminderInvoiceFact withoutPaymentReview(ReminderInvoiceFact fact) {
        return fact.paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW ? fact.withPaymentStatus(InvoicePaymentStatus.PENDING) : fact;
    }

    private static boolean isOpenLateFeeDebitNote(ReminderInvoiceFact fact) {
        return fact.documentType() == InvoiceDocumentType.DEBIT_NOTE && fact.invoiceType() == InvoiceType.LATE_FEE
                && fact.documentStatus() == InvoiceDocumentStatus.ISSUED && OPEN_PAYMENT_STATUSES.contains(fact.paymentStatus());
    }

    private static Optional<PolicyVerdict> firstDenial(PolicyVerdict receipt, PolicyVerdict cancellation) {
        if (!receipt.allowed()) {
            return Optional.of(receipt);
        }
        return cancellation.allowed() ? Optional.empty() : Optional.of(cancellation);
    }

    private static void collectDue(ReminderInvoiceFact fact, ReminderOccurrence occurrence, List<ReminderInvoiceFact> listedDebt, LocalDate businessDate,
                                   NonBusinessDays nonBusinessDays, List<ReminderDueNotice> inAppNotices, List<ReminderDueNotice> emailNotices, List<SuppressedReminder> suppressed) {
        Set<ReminderChannel> ownChannels = channelsFiringOn(occurrence, businessDate, nonBusinessDays);
        PolicyVerdict waitsForPriorDebt = WAITS_FOR_PRIOR_DEBT.evaluateRule(new PriorDebtReminderContext(fact, occurrence.messageType(), listedDebt));
        if (!waitsForPriorDebt.allowed()) {
            ownChannels.forEach(channel -> suppressed.add(new SuppressedReminder(occurrence.invoiceId(), occurrence.noticeKey(), channel, waitsForPriorDebt.code())));
            return;
        }
        boolean summarizesPriorDebt = occurrence.messageType() == ReminderMessageType.ISSUANCE_NOTICE && ReminderPriorDebt.existsBefore(fact, listedDebt);
        Set<ReminderChannel> channels = summarizesPriorDebt ? channelsFiringOn(occurrence.withChannel(ReminderChannel.EMAIL), businessDate, nonBusinessDays) : ownChannels;
        ReminderDueNotice notice = new ReminderDueNotice(occurrence.invoiceId(), occurrence.noticeKey(), occurrence.messageType(), summarizesPriorDebt);
        if (channels.contains(ReminderChannel.IN_APP)) {
            inAppNotices.add(notice);
        }
        if (channels.contains(ReminderChannel.EMAIL)) {
            emailNotices.add(notice);
        }
    }

    private static void collectSuppressed(ReminderOccurrence occurrence, LocalDate businessDate, NonBusinessDays nonBusinessDays, String reasonCode,
                                          List<SuppressedReminder> suppressed) {
        for (ReminderChannel channel : channelsFiringOn(occurrence, businessDate, nonBusinessDays)) {
            suppressed.add(new SuppressedReminder(occurrence.invoiceId(), occurrence.noticeKey(), channel, reasonCode));
        }
    }

    private static Set<ReminderChannel> channelsFiringOn(ReminderOccurrence occurrence, LocalDate businessDate, NonBusinessDays nonBusinessDays) {
        Set<ReminderChannel> channels = EnumSet.noneOf(ReminderChannel.class);
        if (occurrence.inAppDate().filter(businessDate::equals).isPresent()) {
            channels.add(ReminderChannel.IN_APP);
        }
        if (occurrence.emailDate(nonBusinessDays).filter(businessDate::equals).isPresent()) {
            channels.add(ReminderChannel.EMAIL);
        }
        return channels;
    }

    private static ReminderDebtEntry debtEntryOf(ReminderInvoiceFact fact, ResolvedReminderSchedule schedule) {
        ReminderConfigurationVersions versions = schedule == null ? ReminderConfigurationVersions.none() : ReminderConfigurationVersions.of(schedule);
        return new ReminderDebtEntry(fact, versions, !RECEIPT_UNDER_REVIEW.evaluateRule(fact).allowed());
    }
}
