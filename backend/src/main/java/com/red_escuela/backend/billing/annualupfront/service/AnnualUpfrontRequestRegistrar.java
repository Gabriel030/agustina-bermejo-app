package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestRegistration;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTerms;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestRejectedException;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontTermsNotAcceptedException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.discount.domain.AnnualUpfrontReservation;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
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
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

@Service
public class AnnualUpfrontRequestRegistrar {
    private static final int MAX_WINNER_STABILIZATION_ROUNDS = 5;
    private static final String RESERVATION_REASON = "Reserva del descuento por pedido de pago anual";

    private final AnnualUpfrontQuoteService quoteService;
    private final CurrentStudentService currentStudentService;
    private final StudentCurrentAccountService accountService;
    private final EnrollmentDiscountLock enrollmentLock;
    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final InvoiceRepository invoiceRepository;
    private final AnnualUpfrontRequestRepository requestRepository;
    private final Clock clock;

    public AnnualUpfrontRequestRegistrar(AnnualUpfrontQuoteService quoteService, CurrentStudentService currentStudentService, StudentCurrentAccountService accountService,
                                         EnrollmentDiscountLock enrollmentLock, DiscountDefinitionRepository definitionRepository,
                                         StudentDiscountRepository studentDiscountRepository, InvoiceRepository invoiceRepository,
                                         AnnualUpfrontRequestRepository requestRepository, Clock clock) {
        this.quoteService = quoteService;
        this.currentStudentService = currentStudentService;
        this.accountService = accountService;
        this.enrollmentLock = enrollmentLock;
        this.definitionRepository = definitionRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.invoiceRepository = invoiceRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Transactional
    public RegisteredAnnualUpfrontRequest registerRequestOfCurrentStudent(Long programEnrollmentId, String acceptedTermsVersion) {
        if (!AnnualUpfrontTerms.CURRENT.isAcceptedBy(acceptedTermsVersion)) {
            throw new AnnualUpfrontTermsNotAcceptedException();
        }
        Long personId = currentStudentService.resolveCurrentStudent().getPerson().getId();
        AnnualUpfrontRequestPreparation unlocked = quoteService.prepareRequestForCurrentStudent(programEnrollmentId);
        BillingEnrollmentFacts facts = unlocked.facts();
        StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(facts.studentId(), facts.currency());
        enrollmentLock.lockDiscountsOf(EnrollmentReference.ofProgram(programEnrollmentId));
        AnnualUpfrontRequestPreparation preparation = prepareLockingEveryWinningDefinition(programEnrollmentId);
        if (!preparation.eligibility().eligible()) {
            throw new AnnualUpfrontRequestRejectedException(List.of(preparation.eligibility().violation()));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Integer, InvoiceDiscountDraft> discounts = reserveWinningDiscount(preparation, programEnrollmentId, personId);
        Invoice invoice = buildInvoiceAwaitingPayment(preparation, account.getId(), programEnrollmentId, discounts, StatusChangeActor.ofPerson(personId));
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        AnnualUpfrontRequest request = requestRepository.saveAndFlush(AnnualUpfrontRequest.registerRequest(new AnnualUpfrontRequestRegistration(programEnrollmentId,
                preparation.lectiveYearTermId(), saved.getId(), personId, now, AnnualUpfrontTerms.CURRENT)));
        return new RegisteredAnnualUpfrontRequest(request, saved);
    }

    private AnnualUpfrontRequestPreparation prepareLockingEveryWinningDefinition(Long programEnrollmentId) {
        Set<Long> locked = new TreeSet<>();
        for (int round = 0; round < MAX_WINNER_STABILIZATION_ROUNDS; round++) {
            AnnualUpfrontRequestPreparation preparation = quoteService.prepareRequestForCurrentStudent(programEnrollmentId);
            Set<Long> winning = new TreeSet<>(preparation.winners().values().stream().map(InvoiceDiscountCandidate::sourceId).toList());
            if (locked.containsAll(winning)) {
                return preparation;
            }
            winning.stream().filter(id -> !locked.contains(id)).forEach(id -> definitionRepository.findByIdForUpdate(id).orElseThrow());
            locked.addAll(winning);
        }
        throw new IllegalStateException("The winning annual upfront promotion did not stabilize while locking its definitions");
    }

    private Map<Integer, InvoiceDiscountDraft> reserveWinningDiscount(AnnualUpfrontRequestPreparation preparation, Long programEnrollmentId, Long personId) {
        Map<Integer, InvoiceDiscountDraft> drafts = new TreeMap<>();
        if (preparation.winners().isEmpty()) {
            return drafts;
        }
        Long definitionId = preparation.winners().values().iterator().next().sourceId();
        DiscountDefinition definition = definitionRepository.findById(definitionId).orElseThrow();
        List<YearMonth> periods = preparation.periods();
        StudentDiscount reserved = studentDiscountRepository.saveAndFlush(StudentDiscount.reserveAnnualUpfrontDiscount(new AnnualUpfrontReservation(programEnrollmentId,
                preparation.facts().studentId(), preparation.facts().currency(), definition, new ScholarshipPeriodRange(periods.get(0), periods.get(periods.size() - 1)), personId,
                RESERVATION_REASON), Instant.now(clock)));
        preparation.winners().forEach((lineNumber, winner) -> drafts.put(lineNumber, winner.withStudentDiscountId(reserved.getId()).toDiscountDraft()));
        return drafts;
    }

    private Invoice buildInvoiceAwaitingPayment(AnnualUpfrontRequestPreparation preparation, Long accountId, Long programEnrollmentId, Map<Integer, InvoiceDiscountDraft> discounts,
                                                StatusChangeActor actor) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, preparation.facts().currency(), InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT,
                InvoiceIssuingReason.ANNUAL_UPFRONT_PAYMENT, null);
        List<InvoiceLineDraft> lines = IntStream.range(0, preparation.periods().size()).mapToObj(index -> lineOf(preparation, programEnrollmentId, preparation.periods().get(index))).toList();
        Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines, discounts, actor);
        draft.submitDraftForPayment(preparation.eligibility().quote().paymentDeadline(), actor);
        return draft;
    }

    private static InvoiceLineDraft lineOf(AnnualUpfrontRequestPreparation preparation, Long programEnrollmentId, YearMonth period) {
        return new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + period, period, programEnrollmentId, null, null, null, preparation.price().priceVersionId(),
                ChargeKey.forProgramEnrollmentMonthlyInstallment(programEnrollmentId, period).value(), preparation.price().finalAmount(), preparation.facts().billingTemplateId(), null,
                preparation.price().marketingDiscountAmount());
    }
}
