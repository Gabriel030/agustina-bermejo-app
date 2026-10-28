package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuote;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuoteCalculator;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontEnrollmentNotFoundException;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRequestCheck;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontDiscountCandidateFinder;
import com.red_escuela.backend.billing.discount.service.ScholarshipCoverageReader;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.service.EnrollmentInvoiceQueryService;
import com.red_escuela.backend.billing.invoice.service.InvoiceFollowUpQueries;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class AnnualUpfrontQuoteService {
    private final BillingEnrollmentLookup enrollmentLookup;
    private final CurrentStudentService currentStudentService;
    private final AnnualUpfrontCycleReader cycleReader;
    private final BillingConfigurationReader configurationReader;
    private final PriceReader priceReader;
    private final EnrollmentInvoiceQueryService enrollmentInvoices;
    private final InvoiceFollowUpQueries invoiceFollowUp;
    private final StudentCurrentAccountService accountService;
    private final ScholarshipCoverageReader scholarshipCoverage;
    private final AnnualUpfrontDiscountCandidateFinder candidateFinder;
    private final AnnualUpfrontDiscountSelector discountSelector;
    private final PolicySet<AnnualUpfrontRequestCheck> eligibilityPolicies;
    private final Clock clock;

    public AnnualUpfrontQuoteService(BillingEnrollmentLookup enrollmentLookup, CurrentStudentService currentStudentService, AnnualUpfrontCycleReader cycleReader,
                                     BillingConfigurationReader configurationReader, PriceReader priceReader, EnrollmentInvoiceQueryService enrollmentInvoices,
                                     InvoiceFollowUpQueries invoiceFollowUp, StudentCurrentAccountService accountService, ScholarshipCoverageReader scholarshipCoverage,
                                     AnnualUpfrontDiscountCandidateFinder candidateFinder, AnnualUpfrontDiscountSelector discountSelector,
                                     PolicySet<AnnualUpfrontRequestCheck> annualUpfrontRequestPolicies, Clock clock) {
        this.enrollmentLookup = enrollmentLookup;
        this.currentStudentService = currentStudentService;
        this.cycleReader = cycleReader;
        this.configurationReader = configurationReader;
        this.priceReader = priceReader;
        this.enrollmentInvoices = enrollmentInvoices;
        this.invoiceFollowUp = invoiceFollowUp;
        this.accountService = accountService;
        this.scholarshipCoverage = scholarshipCoverage;
        this.candidateFinder = candidateFinder;
        this.discountSelector = discountSelector;
        this.eligibilityPolicies = annualUpfrontRequestPolicies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontEligibility quoteForStaff(Long programEnrollmentId) {
        return quoteFor(programEnrollmentId, findFactsOrThrow(programEnrollmentId));
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontEligibility quoteForCurrentStudent(Long programEnrollmentId) {
        BillingEnrollmentFacts facts = findFactsOrThrow(programEnrollmentId);
        if (!facts.studentId().equals(currentStudentService.resolveCurrentStudent().getId())) {
            throw new AnnualUpfrontEnrollmentNotFoundException(programEnrollmentId);
        }
        return quoteFor(programEnrollmentId, facts);
    }

    private BillingEnrollmentFacts findFactsOrThrow(Long programEnrollmentId) {
        return enrollmentLookup.findProgramEnrollmentFacts(programEnrollmentId).orElseThrow(() -> new AnnualUpfrontEnrollmentNotFoundException(programEnrollmentId));
    }

    @Transactional
    public AnnualUpfrontRequestPreparation prepareRequestForCurrentStudent(Long programEnrollmentId) {
        BillingEnrollmentFacts facts = findFactsOrThrow(programEnrollmentId);
        if (!facts.studentId().equals(currentStudentService.resolveCurrentStudent().getId())) {
            throw new AnnualUpfrontEnrollmentNotFoundException(programEnrollmentId);
        }
        return prepareRequest(programEnrollmentId, facts);
    }

    private AnnualUpfrontEligibility quoteFor(Long programEnrollmentId, BillingEnrollmentFacts facts) {
        return prepareRequest(programEnrollmentId, facts).eligibility();
    }

    private AnnualUpfrontRequestPreparation prepareRequest(Long programEnrollmentId, BillingEnrollmentFacts facts) {
        LocalDate today = LocalDate.now(clock);
        Optional<YearCycle> cycle = cycleReader.findCycleToQuote(facts.programId(), facts.billingTemplateId(), today);
        List<YearMonth> periods = cycle.map(AnnualUpfrontQuoteService::periodsOf).orElse(List.of());
        Optional<AnnualUpfrontSettings> settings = cycle.flatMap(found -> configurationReader.findAnnualUpfrontSettingsEffectiveOn(facts.programId(), found.template().termId(), today));
        Optional<PriceVersionResolution> price = priceReader.findPriceVersionEffectiveOn(OfferingReference.ofProgram(facts.programId()), FeeType.MONTHLY_INSTALLMENT, today);
        List<Invoice> invoices = enrollmentInvoices.findInvoicesOfEnrollment(programEnrollmentId, null);
        AnnualUpfrontRequestCheck check = new AnnualUpfrontRequestCheck(facts.active(), today, settings.map(AnnualUpfrontSettings::requestDeadline).orElse(null),
                cycle.isPresent() && scholarshipCoverage.activeScholarshipCoversAnyMonthOf(programEnrollmentId, cycle.get().termStart(), cycle.get().end()),
                hasOverdueDebt(facts.studentId(), today), findOpenRequestInvoiceId(invoices, cycle).orElse(null), monthlyInstallmentIssued(invoices, cycle),
                periods.size(), price.isPresent());
        Integer lectiveYear = cycle.map(found -> found.termStart().getYear()).orElse(null);
        PolicyEvaluationResult result = eligibilityPolicies.evaluateRulesUntilFirstViolation(check);
        if (!result.allowed()) {
            PolicyViolation violation = result.violations().get(0);
            return AnnualUpfrontRequestPreparation.rejected(facts, AnnualUpfrontEligibility.notEligible(violation, check.openRequestInvoiceId(), lectiveYear, check.requestDeadline()));
        }
        LocalDateTime paymentDeadline = LocalDateTime.now(clock).plusHours(settings.orElseThrow().paymentDeadlineHours());
        PriceVersionResolution resolvedPrice = price.orElseThrow();
        Map<Integer, InvoiceDiscountCandidate> winners = selectWinners(facts, today, periods, resolvedPrice);
        AnnualUpfrontQuote quote = AnnualUpfrontQuoteCalculator.calculateQuote(periods, resolvedPrice.finalAmount(), winners, paymentDeadline);
        return AnnualUpfrontRequestPreparation.accepted(facts, AnnualUpfrontEligibility.eligible(lectiveYear, check.requestDeadline(), quote), cycle.orElseThrow().template().termId(),
                periods, resolvedPrice, winners);
    }

    private Map<Integer, InvoiceDiscountCandidate> selectWinners(BillingEnrollmentFacts facts, LocalDate today, List<YearMonth> periods, PriceVersionResolution price) {
        NonNegativeMoney pricePerMonth = price.finalAmount();
        List<InvoiceDiscountCandidate> candidates = candidateFinder.findCandidatesForLines(facts.programId(), facts.currency(), today,
                IntStream.range(0, periods.size()).mapToObj(index -> pricePerMonth).toList());
        return discountSelector.selectWinningPromotionForEveryLine(candidates);
    }

    private boolean hasOverdueDebt(Long studentId, LocalDate today) {
        return accountService.findStudentCurrentAccountIds(studentId).stream().anyMatch(accountId -> invoiceFollowUp.hasUnpaidIssuedDocumentOverdue(accountId, today));
    }

    private static List<YearMonth> periodsOf(YearCycle cycle) {
        return IntStream.range(0, cycle.yearTerms().recurringFeeInvoicesCount())
                .mapToObj(offset -> cycle.termStart().plusMonths(offset))
                .filter(period -> !period.isAfter(cycle.end()))
                .toList();
    }

    private static Optional<Long> findOpenRequestInvoiceId(List<Invoice> invoices, Optional<YearCycle> cycle) {
        return invoices.stream()
                .filter(invoice -> invoice.getInvoiceType() == InvoiceType.ANNUAL_UPFRONT && isLive(invoice) && cycle.map(found -> isInCycle(invoice, found)).orElse(false))
                .map(Invoice::getId)
                .findFirst();
    }

    private static boolean monthlyInstallmentIssued(List<Invoice> invoices, Optional<YearCycle> cycle) {
        return invoices.stream()
                .anyMatch(invoice -> invoice.getInvoiceType() == InvoiceType.MONTHLY_INSTALLMENT && isLive(invoice) && cycle.map(found -> isInCycle(invoice, found)).orElse(false));
    }

    private static boolean isLive(Invoice invoice) {
        return invoice.getDocumentStatus() != InvoiceDocumentStatus.DISCARDED && !invoice.getPrimaryLine().getChargeKeyReleased();
    }

    private static boolean isInCycle(Invoice invoice, YearCycle cycle) {
        return invoice.getLines().stream().anyMatch(line -> line.getPeriod() != null && cycle.installmentRangeContains(line.getPeriod()));
    }
}
