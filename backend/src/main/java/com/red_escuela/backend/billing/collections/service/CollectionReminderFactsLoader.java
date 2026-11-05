package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.ReminderContentTerms;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderLateFeeTerms;
import com.red_escuela.backend.billing.collections.domain.ReminderSuspensionRule;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.collections.repository.ReminderFactsQuery;
import com.red_escuela.backend.billing.collections.repository.ReminderFactsQuery.CreditRow;
import com.red_escuela.backend.billing.collections.repository.ReminderFactsQuery.OpenInvoiceRow;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.enrollment.api.EnrollmentReminderTarget;
import com.red_escuela.backend.enrollment.api.EnrollmentReminderTargetFinder;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CollectionReminderFactsLoader {

    public static final LocalTime PLANNING_RUN_TIME = LocalTime.of(5, 0);

    private final ReminderFactsQuery factsQuery;
    private final EnrollmentReminderTargetFinder enrollmentTargets;
    private final BillingConfigurationReader configurationReader;
    private final PendingReceiptProtectionWindow protectionWindow;

    public CollectionReminderFactsLoader(ReminderFactsQuery factsQuery, EnrollmentReminderTargetFinder enrollmentTargets, BillingConfigurationReader configurationReader, PendingReceiptProtectionWindow protectionWindow) {
        this.factsQuery = factsQuery;
        this.enrollmentTargets = enrollmentTargets;
        this.configurationReader = configurationReader;
        this.protectionWindow = protectionWindow;
    }

    @Transactional(readOnly = true)
    public List<Long> findCandidateStudentIdsAfter(Long afterStudentId, int limit) {
        return factsQuery.findStudentIdsWithRemindableInvoicesAfter(afterStudentId, limit);
    }

    @Transactional(readOnly = true)
    public List<Long> findCandidateStudentIdsAmong(Collection<Long> studentIds) {
        return factsQuery.findStudentIdsWithRemindableInvoicesAmong(studentIds);
    }

    @Transactional(readOnly = true)
    public List<ReminderInvoiceFact> loadInvoiceFactsOf(Collection<Long> invoiceIds, LocalDate businessDate) {
        return factsQuery.findOpenInvoicesByIds(invoiceIds, businessDate, protectionWindow.earliestProtectedUploadDateAsOf(businessDate)).stream().map(row -> factOf(row, Collections.emptyMap(), Collections.emptyMap())).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentReminderContext> loadContextsOf(Collection<Long> studentIds, LocalDate businessDate) {
        LocalDate previousBusinessDate = businessDate.minusDays(1);
        List<OpenInvoiceRow> invoiceRows = factsQuery.findOpenInvoicesOf(studentIds, previousBusinessDate, previousBusinessDate.atTime(PLANNING_RUN_TIME),
                protectionWindow.earliestProtectedUploadDateAsOf(businessDate));
        Map<Long, EnrollmentReminderTarget> programTargets = enrollmentTargets.findProgramEnrollmentTargets(distinctIdsOf(invoiceRows, OpenInvoiceRow::programEnrollmentId));
        Map<Long, EnrollmentReminderTarget> courseTargets = enrollmentTargets.findCourseEnrollmentTargets(distinctIdsOf(invoiceRows, OpenInvoiceRow::courseEnrollmentId));
        Map<Long, List<CreditRow>> creditsByStudent = factsQuery.findUnallocatedCreditOf(studentIds).stream().collect(Collectors.groupingBy(CreditRow::studentId));
        Map<Long, Long> personByStudent = factsQuery.findPersonsOf(studentIds).stream().collect(Collectors.toMap(ReminderFactsQuery.StudentPersonRow::studentId,
                ReminderFactsQuery.StudentPersonRow::personId));
        Map<Long, List<OpenInvoiceRow>> invoicesByStudent = invoiceRows.stream().collect(Collectors.groupingBy(OpenInvoiceRow::studentId, LinkedHashMap::new, Collectors.toList()));
        List<StudentReminderContext> contexts = new ArrayList<>();
        for (Map.Entry<Long, List<OpenInvoiceRow>> entry : invoicesByStudent.entrySet()) {
            Long personId = personByStudent.get(entry.getKey());
            if (personId == null) {
                continue;
            }
            List<ReminderInvoiceFact> facts = entry.getValue().stream().map(row -> factOf(row, programTargets, courseTargets)).toList();
            Map<Long, ReminderScheduleTarget> scheduleTargets = new HashMap<>();
            for (OpenInvoiceRow row : entry.getValue()) {
                scheduleTargetOf(row, programTargets, courseTargets).ifPresent(target -> scheduleTargets.put(row.invoiceId(), target));
            }
            contexts.add(new StudentReminderContext(new ReminderStudentFacts(entry.getKey(), facts, creditOf(creditsByStudent.getOrDefault(entry.getKey(), List.of()))),
                    personId, scheduleTargets));
        }
        return contexts;
    }

    public Map<Long, ResolvedReminderSchedule> resolveSchedulesOf(StudentReminderContext context, LocalDate businessDate,
                                                                   Map<ReminderScheduleTarget, ResolvedReminderSchedule> resolvedByTarget) {
        Map<Long, ResolvedReminderSchedule> schedules = new HashMap<>();
        context.scheduleTargetsByInvoiceId().forEach((invoiceId, target) ->
                schedules.put(invoiceId, resolvedByTarget.computeIfAbsent(target, key -> configurationReader.reminderScheduleFor(key, businessDate))));
        return schedules;
    }

    public ReminderContentTerms resolveContentTermsOf(StudentReminderContext context, LocalDate businessDate, ReminderContentTermsCache cache) {
        Map<Long, ReminderLateFeeTerms> lateFeeByInvoiceId = new HashMap<>();
        for (ReminderInvoiceFact fact : context.facts().invoices()) {
            if (!fact.isDebitNote() && fact.invoiceType() != InvoiceType.LATE_FEE) {
                cache.latePaymentTermsOn(fact.dueDate(), this::latePaymentTermsInEffectOn).flatMap(terms -> ReminderLateFeeTerms.of(terms, fact.finalPriceOrBalance()))
                        .ifPresent(terms -> lateFeeByInvoiceId.put(fact.invoiceId(), terms));
            }
        }
        ReminderSuspensionRule suspensionRule = cache.suspensionRuleFrom(() -> latePaymentTermsInEffectOn(businessDate).flatMap(ReminderSuspensionRule::of)).orElse(null);
        return new ReminderContentTerms(lateFeeByInvoiceId, suspensionRule);
    }

    private Optional<LatePaymentTerms> latePaymentTermsInEffectOn(LocalDate date) {
        return configurationReader.findLatePaymentSettingsEffectiveOn(date).or(configurationReader::findFirstLatePaymentSettings);
    }

    private static Set<Long> distinctIdsOf(List<OpenInvoiceRow> rows, Function<OpenInvoiceRow, Long> idOf) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(row -> {
            if (idOf.apply(row) != null) {
                ids.add(idOf.apply(row));
            }
        });
        return ids;
    }

    private static ReminderInvoiceFact factOf(OpenInvoiceRow row, Map<Long, EnrollmentReminderTarget> programTargets, Map<Long, EnrollmentReminderTarget> courseTargets) {
        Currency currency = Currency.valueOf(row.currency());
        boolean suspended = isSuspended(programTargets.get(row.programEnrollmentId())) || isSuspended(courseTargets.get(row.courseEnrollmentId()));
        return new ReminderInvoiceFact(row.invoiceId(), row.originInvoiceId(), InvoiceDocumentType.valueOf(row.documentType()), InvoiceType.valueOf(row.invoiceType()),
                InvoiceDocumentStatus.valueOf(row.documentStatus()), InvoicePaymentStatus.valueOf(row.paymentStatus()), row.issueDate(), row.dueDate(), null,
                NonNegativeMoney.of(row.outstandingAmount(), currency), row.programEnrollmentId(), row.courseEnrollmentId(), row.receiptUnderReview(),
                row.openCancellationRequest(), suspended, row.issuedAfterPreviousRun(),
                row.finalPrice() == null ? null : NonNegativeMoney.of(row.finalPrice(), currency));
    }

    private static boolean isSuspended(EnrollmentReminderTarget target) {
        return target != null && target.suspended();
    }

    private static Optional<ReminderScheduleTarget> scheduleTargetOf(OpenInvoiceRow row, Map<Long, EnrollmentReminderTarget> programTargets,
                                                                               Map<Long, EnrollmentReminderTarget> courseTargets) {
        EnrollmentReminderTarget program = programTargets.get(row.programEnrollmentId());
        if (program != null && program.programId() != null) {
            return Optional.of(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, program.programId()));
        }
        EnrollmentReminderTarget course = courseTargets.get(row.courseEnrollmentId());
        if (course != null && course.courseId() != null) {
            return Optional.of(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, course.courseId()));
        }
        return Optional.empty();
    }

    private static Map<Currency, NonNegativeMoney> creditOf(List<CreditRow> rows) {
        Map<Currency, NonNegativeMoney> credit = new EnumMap<>(Currency.class);
        rows.forEach(row -> credit.put(Currency.valueOf(row.currency()), NonNegativeMoney.of(row.amount(), Currency.valueOf(row.currency()))));
        return credit;
    }
}
