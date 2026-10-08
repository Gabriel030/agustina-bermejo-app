package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeObligationStatus;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.domain.ObligationInvoiceFacts;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeRequirementContext;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotNotBeforeEntrySpecification;
import com.red_escuela.backend.billing.planning.policy.PlanningPolicySetsConfiguration;
import com.red_escuela.backend.billing.planning.service.EnrollmentFeeObligationStatusClassifier;
import com.red_escuela.backend.billing.planning.service.EnrollmentFeeReferenceSlotResolver;
import com.red_escuela.backend.billing.planning.service.EnrollmentFeeRequirementOutcome;
import com.red_escuela.backend.billing.planning.service.EnrollmentFeeSlotReference;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidForTermException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class EnrollmentFeeRequirementService {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentFeeRequirementService.class);

    public record Obligation(Long templateId, int slot, int slotCount, YearMonth periodFrom, YearMonth periodTo, EnrollmentFeeObligationStatus status,
                             Long invoiceId, LocalDate dueDate, Money remainingBalance, boolean satisfied) {
    }

    private final InvoicePlanningContextLoader contextLoader;
    private final InvoiceRepository invoiceRepository;
    private final BankTransferReceiptRepository receiptRepository;
    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final PlannedInvoiceIssuingService plannedInvoiceIssuingService;
    private final ProgramMonthlyInvoicePlanner planner;
    private final PlanningPolicySetsConfiguration policySets;
    private final EnrollmentFeeReferenceSlotResolver referenceSlotResolver = new EnrollmentFeeReferenceSlotResolver();
    private final EnrollmentFeeObligationStatusClassifier statusClassifier = new EnrollmentFeeObligationStatusClassifier();
    private final Clock clock;

    public EnrollmentFeeRequirementService(InvoicePlanningContextLoader contextLoader, InvoiceRepository invoiceRepository,
                                           BankTransferReceiptRepository receiptRepository, EnrollmentFeeExemptionRepository exemptionRepository,
                                           PlannedInvoiceIssuingService plannedInvoiceIssuingService, ProgramMonthlyInvoicePlanner planner,
                                           PlanningPolicySetsConfiguration policySets, Clock clock) {
        this.contextLoader = contextLoader;
        this.invoiceRepository = invoiceRepository;
        this.receiptRepository = receiptRepository;
        this.exemptionRepository = exemptionRepository;
        this.plannedInvoiceIssuingService = plannedInvoiceIssuingService;
        this.planner = planner;
        this.policySets = policySets;
        this.clock = clock;
    }

    @Transactional
    public void requireForTerm(ProgramEnrollment enrollment, Term term) {
        requireForTerm(enrollment, term, EnrollmentFeeRequirementScope.SUBJECT_ENROLLMENT);
    }

    @Transactional
    public void requireForTerm(ProgramEnrollment enrollment, Term term, EnrollmentFeeRequirementScope scope) {
        LocalDate today = LocalDate.now(clock);
        YearMonth reference = Optional.ofNullable(term.getStartDate()).or(() -> Optional.ofNullable(term.getEnrollmentStart()))
                .map(YearMonth::from).orElseGet(() -> YearMonth.from(today));
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        referenceSlotResolver.resolveReferenceSlotForPeriod(context.facts(), reference)
                .ifPresent(slot -> requireEnrollmentFee(enrollment, context, slot, scope));
    }

    @Transactional
    public void requireForCurrentPeriod(ProgramEnrollment enrollment, EnrollmentFeeRequirementScope scope) {
        LocalDate today = LocalDate.now(clock);
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, YearMonth.from(today), today);
        referenceSlotResolver.resolveLatestDueSlot(context.facts()).ifPresent(slot -> requireEnrollmentFee(enrollment, context, slot, scope));
    }

    @Transactional(readOnly = true)
    public List<Obligation> statusOf(ProgramEnrollment enrollment) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        ProgramInvoicePlanningFacts facts = contextLoader.load(enrollment, currentMonth, today).facts();
        Stream<YearCycle> relevantCycles = Stream.concat(facts.cycles().latestStartedOn(currentMonth).stream(),
                facts.cycles().all().stream().filter(cycle -> cycle.start().isAfter(currentMonth)).findFirst().stream());
        return relevantCycles
                .flatMap(cycle -> IntStream.rangeClosed(1, cycle.slotCount()).mapToObj(slot -> new EnrollmentFeeSlotReference(cycle, slot)))
                .filter(reference -> !EnrollmentFeeSlotNotBeforeEntrySpecification.isSlotBeforeEntry(facts, reference.cycle(), reference.slot()))
                .map(reference -> describeObligation(enrollment, facts, reference.cycle(), reference.slot()))
                .toList();
    }

    private void requireEnrollmentFee(ProgramEnrollment enrollment, InvoicePlanningContextLoader.PlanningContext context, EnrollmentFeeSlotReference reference,
                                      EnrollmentFeeRequirementScope scope) {
        ProgramInvoicePlanningFacts facts = context.facts();
        YearCycle cycle = reference.cycle();
        EnrollmentFeeRequirementContext requirementContext = new EnrollmentFeeRequirementContext(scope, facts, cycle, reference.slot(), cycle.terms(),
                exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(enrollment.getId(), cycle.templateId(), reference.slot()).isPresent(),
                facts.feeOf(cycle, reference.slot()));
        outcomeOf(enrollment, context, requirementContext).resolveEnrollmentFeeRequirement(
                charge -> issueOnDemand(enrollment, context, cycle, charge),
                violations -> new EnrollmentFeeNotPaidForTermException(enrollment.getId(), cycle.templateId(), reference.slot()));
    }

    private EnrollmentFeeRequirementOutcome outcomeOf(ProgramEnrollment enrollment, InvoicePlanningContextLoader.PlanningContext context,
                                                      EnrollmentFeeRequirementContext requirementContext) {
        return policySets.enrollmentFeeRequirementApplicability().evaluateRulesUntilFirstViolation(requirementContext).firstDenial()
                .<EnrollmentFeeRequirementOutcome>map(exemption -> notRequired(enrollment, exemption))
                .orElseGet(() -> outcomeOfRequirement(context, requirementContext));
    }

    private EnrollmentFeeRequirementOutcome notRequired(ProgramEnrollment enrollment, PolicyVerdict exemption) {
        log.info("Matrícula no exigida: inscripcion={}, policy={}, code={}", enrollment.getId(), exemption.policy(), exemption.code());
        return new EnrollmentFeeRequirementOutcome.NotRequired(exemption.code());
    }

    private EnrollmentFeeRequirementOutcome outcomeOfRequirement(InvoicePlanningContextLoader.PlanningContext context,
                                                                 EnrollmentFeeRequirementContext requirementContext) {
        PolicyEvaluationResult result = policySets.enrollmentFeeRequirement().evaluateRulesUntilFirstViolation(requirementContext);
        Map<String, Supplier<EnrollmentFeeRequirementOutcome>> outcomeByViolation = Map.of(
                "ENROLLMENT_FEE_NOT_ISSUED", () -> outcomeForUnissuedFee(context, requirementContext, result),
                "ENROLLMENT_FEE_NOT_PAID_FOR_TERM", () -> new EnrollmentFeeRequirementOutcome.Block(result));
        return result.firstDenial().map(denial -> outcomeByViolation.get(denial.code()).get())
                .orElseGet(EnrollmentFeeRequirementOutcome.Settled::new);
    }

    private EnrollmentFeeRequirementOutcome outcomeForUnissuedFee(InvoicePlanningContextLoader.PlanningContext context,
                                                                  EnrollmentFeeRequirementContext requirementContext, PolicyEvaluationResult result) {
        return planner.planInvoicesForMonth(context.facts(), InvoicePlanningScope.ENROLLMENT_FEE_ONLY, true).charges().stream()
                .filter(charge -> requirementContext.cycle().templateId().equals(charge.templateId()) && Optional.ofNullable(charge.slot()).filter(slot -> slot == requirementContext.slot()).isPresent())
                .findFirst()
                .<EnrollmentFeeRequirementOutcome>map(charge -> new EnrollmentFeeRequirementOutcome.IssueOnDemandAndBlock(charge, result))
                .orElseGet(() -> new EnrollmentFeeRequirementOutcome.NotRequired("ENROLLMENT_FEE_NOT_ISSUED"));
    }

    private void issueOnDemand(ProgramEnrollment enrollment, InvoicePlanningContextLoader.PlanningContext context, YearCycle cycle, PlannedInvoice charge) {
        log.info("Matrícula emitida a demanda: inscripcion={}, plantilla={}, matricula={}, periodo={}",
                enrollment.getId(), cycle.templateId(), charge.slot(), charge.period());
        plannedInvoiceIssuingService.issuePlannedProgramInvoice(enrollment, context.selection(), charge, InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND, false);
    }

    private Obligation describeObligation(ProgramEnrollment enrollment, ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot) {
        boolean exempted = exemptionRepository
                .findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(enrollment.getId(), cycle.templateId(), slot).isPresent();
        Optional<Invoice> invoice = facts.feeOf(cycle, slot).flatMap(fee -> invoiceRepository.findById(fee.invoiceId()));
        EnrollmentFeeObligationStatus status = statusClassifier.classifyEnrollmentFeeObligation(
                new EnrollmentFeeObligationStatusClassifier.Evidence(exempted, invoice.map(this::invoiceFactsOf)));
        return new Obligation(cycle.templateId(), slot, cycle.slotCount(), cycle.slotFrom(slot), cycle.slotTo(slot), status,
                invoice.map(Invoice::getId).orElse(null), invoice.map(Invoice::getDueDate).orElse(null),
                invoice.filter(found -> !status.isSatisfied()).map(Invoice::getRemainingBalance).orElse(null), status.isSatisfied());
    }

    private ObligationInvoiceFacts invoiceFactsOf(Invoice invoice) {
        return new ObligationInvoiceFacts(invoice.getStatus(),
                receiptRepository.existsByInvoiceIdAndStatus(invoice.getId(), BankTransferReceiptStatus.PENDING_REVIEW),
                invoice.getRemainingBalance().isGreaterThan(invoice.getAmountDue()) || invoice.getRemainingBalance().equals(invoice.getAmountDue()));
    }
}
