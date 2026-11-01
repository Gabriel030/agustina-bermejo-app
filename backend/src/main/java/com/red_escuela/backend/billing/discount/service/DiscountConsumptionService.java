package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.discount.domain.DiscountEnrollmentIds;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LiveInvoiceDiscount;
import com.red_escuela.backend.billing.discount.domain.ReleasedDiscountConsumption;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceDiscardedEvent;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class DiscountConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(DiscountConsumptionService.class);
    private static final String PROCESS_NAME = "INVOICE_CANCELLATION";

    private final AppliedStudentDiscountRepository appliedRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final EnrollmentDiscountLock enrollmentLock;
    private final Clock clock;

    public DiscountConsumptionService(AppliedStudentDiscountRepository appliedRepository, StudentDiscountRepository studentDiscountRepository,
                                      EnrollmentDiscountLock enrollmentLock, Clock clock) {
        this.appliedRepository = appliedRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.enrollmentLock = enrollmentLock;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEnrollmentsOfDiscountsAppliedOn(Long invoiceId) {
        studentDiscountRepository.findEnrollmentsOfDiscountsLiveOnInvoice(invoiceId).stream()
                .flatMap(this::referencesOf)
                .sorted(Comparator.comparing(EnrollmentReference::kind).thenComparing(EnrollmentReference::id))
                .forEach(enrollmentLock::lockDiscountsOf);
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @EventListener
    public void releaseDiscountConsumption(InvoiceCancelledEvent event) {
        releaseConsumptionOfInvoice(event.invoiceId());
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @EventListener
    public void releaseDiscountConsumption(InvoiceDiscardedEvent event) {
        releaseConsumptionOfInvoice(event.invoiceId());
    }

    private void releaseConsumptionOfInvoice(Long invoiceId) {
        List<LiveInvoiceDiscount> live = appliedRepository.findLiveDiscountsOfInvoice(invoiceId);
        if (live.isEmpty()) {
            return;
        }
        List<Long> studentDiscountIds = live.stream().map(LiveInvoiceDiscount::studentDiscountId).distinct().sorted().toList();
        List<StudentDiscount> discounts = studentDiscountIds.stream().map(id -> studentDiscountRepository.findByIdForUpdate(id).orElseThrow()).toList();
        appliedRepository.markConsumptionReleased(live.stream().map(LiveInvoiceDiscount::invoiceDiscountId).toList(), LocalDateTime.now(clock));
        Instant now = Instant.now(clock);
        discounts.stream().filter(DiscountConsumptionService::returnsInstallmentConsumption).forEach(discount -> {
            discount.returnInstallmentConsumption(StatusChangeActor.ofProcess(PROCESS_NAME), now);
            studentDiscountRepository.save(discount);
        });
        log.info("Consumo de descuentos devuelto por cancelacion o descarte: factura={}, descuentos={}, aplicaciones={}", invoiceId, studentDiscountIds, live.size());
    }

    @Transactional(readOnly = true)
    public List<ReleasedDiscountConsumption> describeConsumptionReleasedByCancellationOf(Long invoiceId) {
        List<Long> studentDiscountIds = appliedRepository.findLiveDiscountsOfInvoice(invoiceId).stream().map(LiveInvoiceDiscount::studentDiscountId).distinct().sorted().toList();
        return studentDiscountRepository.findAllById(studentDiscountIds).stream()
                .sorted(Comparator.comparing(StudentDiscount::getId))
                .map(discount -> new ReleasedDiscountConsumption(discount.getId(), returnsInstallmentConsumption(discount))).toList();
    }

    private static boolean returnsInstallmentConsumption(StudentDiscount discount) {
        return discount.getStatus() == StudentDiscountStatus.EXHAUSTED;
    }

    private Stream<EnrollmentReference> referencesOf(DiscountEnrollmentIds ids) {
        return Stream.concat(
                ids.programEnrollmentId() == null ? Stream.empty() : Stream.of(EnrollmentReference.ofProgram(ids.programEnrollmentId())),
                ids.courseEnrollmentId() == null ? Stream.empty() : Stream.of(EnrollmentReference.ofCourse(ids.courseEnrollmentId())));
    }
}
