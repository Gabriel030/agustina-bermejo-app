package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestRegistration;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTerms;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontIdempotencyKeyInvalidException;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontTermsNotAcceptedException;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontIdempotencyKey;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRejections;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRequestCheck;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontTermsAcceptance;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontDiscountReservationService;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscountDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.service.InvoiceAwaitingPaymentRegistrationService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

@Component
public class RequestAnnualUpfrontOperation implements BillingOperation<RequestAnnualUpfrontCommand, RegisteredAnnualUpfrontRequest> {
    private static final int MAX_WINNER_STABILIZATION_ROUNDS = 5;

    private final PolicySet<AnnualUpfrontIdempotencyKey> idempotencyKeyPolicies;
    private final PolicySet<AnnualUpfrontTermsAcceptance> termsPolicies;
    private final PolicySet<AnnualUpfrontRequestCheck> eligibilityPolicies;
    private final AnnualUpfrontQuoteService quoteService;
    private final StudentCurrentAccountService accountService;
    private final EnrollmentDiscountLock enrollmentLock;
    private final AnnualUpfrontDiscountReservationService discountReservations;
    private final InvoiceAwaitingPaymentRegistrationService invoices;
    private final AnnualUpfrontRequestRepository requestRepository;
    private final Clock clock;

    public RequestAnnualUpfrontOperation(PolicySet<AnnualUpfrontIdempotencyKey> annualUpfrontIdempotencyKeyPolicies, PolicySet<AnnualUpfrontTermsAcceptance> annualUpfrontTermsPolicies, PolicySet<AnnualUpfrontRequestCheck> annualUpfrontRequestPolicies,
                                         AnnualUpfrontQuoteService quoteService, StudentCurrentAccountService accountService, EnrollmentDiscountLock enrollmentLock,
                                         AnnualUpfrontDiscountReservationService discountReservations, InvoiceAwaitingPaymentRegistrationService invoices,
                                         AnnualUpfrontRequestRepository requestRepository, Clock clock) {
        this.idempotencyKeyPolicies = annualUpfrontIdempotencyKeyPolicies;
        this.termsPolicies = annualUpfrontTermsPolicies;
        this.eligibilityPolicies = annualUpfrontRequestPolicies;
        this.quoteService = quoteService;
        this.accountService = accountService;
        this.enrollmentLock = enrollmentLock;
        this.discountReservations = discountReservations;
        this.invoices = invoices;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REQUEST_ANNUAL_UPFRONT;
    }

    @Override
    public Class<RequestAnnualUpfrontCommand> contextType() {
        return RequestAnnualUpfrontCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(idempotencyKeyPolicies.moment(), termsPolicies.moment(), eligibilityPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(RequestAnnualUpfrontCommand command) {
        idempotencyKeyPolicies.evaluateRulesUntilFirstViolation(new AnnualUpfrontIdempotencyKey(command.idempotencyKey())).throwIfDenied(AnnualUpfrontIdempotencyKeyInvalidException::new);
        termsPolicies.evaluateRulesUntilFirstViolation(new AnnualUpfrontTermsAcceptance(command.termsAccepted(), command.termsVersion(), AnnualUpfrontTerms.CURRENT.version()))
                .throwIfDenied(AnnualUpfrontTermsNotAcceptedException::new);
    }

    @Override
    public RegisteredAnnualUpfrontRequest processOperation(RequestAnnualUpfrontCommand command) {
        Long programEnrollmentId = command.programEnrollmentId();
        Long personId = command.actor().personId();
        BillingEnrollmentFacts facts = quoteService.prepareRequestForCurrentStudent(programEnrollmentId).facts();
        enrollmentLock.lockDiscountsOf(EnrollmentReference.ofProgram(programEnrollmentId));
        StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(facts.studentId(), facts.currency());
        AnnualUpfrontRequestPreparation preparation = prepareLockingEveryWinningDefinition(programEnrollmentId);
        AnnualUpfrontRejections.requireEligible(preparation.eligibility());
        Map<Integer, InvoiceDiscountDraft> discounts = reserveWinningDiscount(preparation, programEnrollmentId, personId);
        Invoice saved = invoices.saveInvoiceAwaitingPayment(buildInvoiceAwaitingPayment(preparation, account.getId(), programEnrollmentId, discounts, command));
        AnnualUpfrontRequest request = requestRepository.saveAndFlush(AnnualUpfrontRequest.registerRequest(new AnnualUpfrontRequestRegistration(programEnrollmentId,
                preparation.lectiveYearTermId(), saved.getId(), personId, LocalDateTime.now(clock), AnnualUpfrontTerms.CURRENT)));
        return new RegisteredAnnualUpfrontRequest(request.getId(), request.getStatus().name(), saved.getId(), saved.getPaymentDeadline(), saved.getAmounts().total().value(),
                request.getAcceptedAt(), request.getAcceptedTermsVersion());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RequestAnnualUpfrontCommand command, RegisteredAnnualUpfrontRequest result) {
        return List.of();
    }

    private AnnualUpfrontRequestPreparation prepareLockingEveryWinningDefinition(Long programEnrollmentId) {
        Set<Long> locked = new TreeSet<>();
        for (int round = 0; round < MAX_WINNER_STABILIZATION_ROUNDS; round++) {
            AnnualUpfrontRequestPreparation preparation = quoteService.prepareRequestForCurrentStudent(programEnrollmentId);
            Set<Long> winning = new TreeSet<>(preparation.winners().values().stream().map(InvoiceDiscountCandidate::sourceId).toList());
            if (locked.containsAll(winning)) {
                return preparation;
            }
            discountReservations.lockDiscountDefinitionsInAscendingOrder(winning.stream().filter(id -> !locked.contains(id)).toList());
            locked.addAll(winning);
        }
        throw new IllegalStateException("The winning annual upfront promotion did not stabilize while locking its definitions");
    }

    private Map<Integer, InvoiceDiscountDraft> reserveWinningDiscount(AnnualUpfrontRequestPreparation preparation, Long programEnrollmentId, Long personId) {
        Map<Integer, InvoiceDiscountDraft> drafts = new TreeMap<>();
        if (preparation.winners().isEmpty()) {
            return drafts;
        }
        List<YearMonth> periods = preparation.periods();
        Long definitionId = preparation.winners().values().iterator().next().sourceId();
        Long reservedId = discountReservations.reserveAnnualUpfrontDiscount(definitionId, programEnrollmentId, preparation.facts().studentId(), preparation.facts().currency(),
                periods.get(0), periods.get(periods.size() - 1), personId);
        preparation.winners().forEach((lineNumber, winner) -> drafts.put(lineNumber, winner.withStudentDiscountId(reservedId).toDiscountDraft()));
        return drafts;
    }

    private Invoice buildInvoiceAwaitingPayment(AnnualUpfrontRequestPreparation preparation, Long accountId, Long programEnrollmentId, Map<Integer, InvoiceDiscountDraft> discounts,
                                                RequestAnnualUpfrontCommand command) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, preparation.facts().currency(), InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT,
                InvoiceIssuingReason.ANNUAL_UPFRONT_PAYMENT, null);
        List<InvoiceLineDraft> lines = IntStream.range(0, preparation.periods().size()).mapToObj(index -> lineOf(preparation, programEnrollmentId, preparation.periods().get(index))).toList();
        Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines, discounts, command.actor());
        draft.submitDraftForPayment(preparation.eligibility().quote().paymentDeadline(), command.actor());
        return draft;
    }

    private static InvoiceLineDraft lineOf(AnnualUpfrontRequestPreparation preparation, Long programEnrollmentId, YearMonth period) {
        return new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + period, period, programEnrollmentId, null, null, null, preparation.price().priceVersionId(),
                ChargeKey.forProgramEnrollmentMonthlyInstallment(programEnrollmentId, period).value(), preparation.price().finalAmount(), preparation.facts().billingTemplateId(), null,
                preparation.price().marketingDiscountAmount());
    }
}
