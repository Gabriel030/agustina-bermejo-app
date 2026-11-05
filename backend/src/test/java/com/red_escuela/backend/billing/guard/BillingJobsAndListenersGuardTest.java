package com.red_escuela.backend.billing.guard;

import com.red_escuela.backend.billing.collections.service.LateFeeAccrualService;
import com.red_escuela.backend.billing.collections.service.LateFeeCorrectionOnWithdrawalListener;
import com.red_escuela.backend.billing.collections.service.LateFeeOnOverdueInvoiceListener;
import com.red_escuela.backend.billing.collections.service.LateFeeWithdrawalCorrectionService;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.DiscountConsumptionService;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import com.red_escuela.backend.billing.pricing.service.ActivateDuePriceVersionsJob;
import com.red_escuela.backend.billing.pricing.service.PriceVersionActivator;
import com.red_escuela.backend.billing.run.operation.NightlyBillingJob;
import com.red_escuela.backend.billing.run.service.NightlyBillingRunner;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.guard.ModuleGuard;
import com.red_escuela.backend.platform.guard.ModuleGuardAspect;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BillingJobsAndListenersGuardTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-03-10T12:00:00Z"), ZoneOffset.UTC);

    private static ModuleGuardAspect aspectWithBillingIn(BillingMode mode) {
        FeatureSnapshotHolder holder = mock(FeatureSnapshotHolder.class);
        Mockito.when(holder.current()).thenReturn(new FeatureSnapshot(
                Map.of(FeatureCode.BILLING, FeatureState.billing(mode, 0)), Instant.parse("2027-03-10T12:00:00Z")));
        return new ModuleGuardAspect(new ModuleGuard(new BillingModeGate(holder)));
    }

    private static <T> T guarded(T target, BillingMode mode) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspectWithBillingIn(mode));
        return factory.getProxy();
    }

    @Test
    void everyBillingJobAndListenerMethodRequiresBillingInFullMode() {
        List<JavaMethod> jobsAndListeners = ImportedApplicationClasses.main().stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith("com.red_escuela.backend.billing"))
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.getOwner().getPackageName().startsWith("com.red_escuela.backend.billing"))
                .filter(method -> method.isAnnotatedWith(Scheduled.class) || method.isAnnotatedWith(ApplicationModuleListener.class)
                        || method.isAnnotatedWith(EventListener.class) || method.isAnnotatedWith(TransactionalEventListener.class))
                .toList();

        List<String> notFullOnly = jobsAndListeners.stream()
                .filter(method -> !method.tryGetAnnotationOfType(RequiresModule.class)
                        .map(requirement -> RequiresModule.BILLING.equals(requirement.value()) && requirement.minMode() == BillingMode.FULL)
                        .orElse(false))
                .map(JavaMethod::getFullName)
                .toList();

        assertThat(jobsAndListeners).hasSizeGreaterThanOrEqualTo(28);
        assertThat(notFullOnly).isEmpty();
    }

    @Test
    void scheduledJobDoesNothingWhenBillingIsOffOrMirror() {
        for (BillingMode mode : List.of(BillingMode.OFF, BillingMode.MIRROR)) {
            NightlyBillingRunner runner = mock(NightlyBillingRunner.class);
            guarded(new NightlyBillingJob(runner, CLOCK), mode).run();
            verifyNoInteractions(runner);
        }
    }

    @Test
    void scheduledJobRunsWhenBillingIsFull() {
        NightlyBillingRunner runner = mock(NightlyBillingRunner.class);

        guarded(new NightlyBillingJob(runner, CLOCK), BillingMode.FULL).run();

        verify(runner).runNightlyBillingSteps(any(), any());
    }

    @Test
    void jobThatDelegatesToAnActivatorDoesNothingWhenBillingIsOffOrMirror() {
        for (BillingMode mode : List.of(BillingMode.OFF, BillingMode.MIRROR)) {
            PriceVersionActivator activator = mock(PriceVersionActivator.class);
            guarded(new ActivateDuePriceVersionsJob(activator), mode).activateDueVersions();
            verifyNoInteractions(activator);
        }
    }

    @Test
    void jobThatDelegatesToAnActivatorRunsWhenBillingIsFull() {
        PriceVersionActivator activator = mock(PriceVersionActivator.class);

        guarded(new ActivateDuePriceVersionsJob(activator), BillingMode.FULL).activateDueVersions();

        verify(activator).activateDueVersions();
    }

    @Test
    void moduleListenerDoesNothingWhenBillingIsOffOrMirror() {
        for (BillingMode mode : List.of(BillingMode.OFF, BillingMode.MIRROR)) {
            LateFeeAccrualService accrualService = mock(LateFeeAccrualService.class);
            guarded(new LateFeeOnOverdueInvoiceListener(accrualService), mode)
                    .reconcileLateFeesOfOverdueInvoice(new InvoiceMarkedAsOverdueEvent(7L, TODAY.minusDays(3), TODAY));
            verifyNoInteractions(accrualService);
        }
    }

    @Test
    void moduleListenerRunsWhenBillingIsFull() {
        LateFeeAccrualService accrualService = mock(LateFeeAccrualService.class);

        guarded(new LateFeeOnOverdueInvoiceListener(accrualService), BillingMode.FULL)
                .reconcileLateFeesOfOverdueInvoice(new InvoiceMarkedAsOverdueEvent(7L, TODAY.minusDays(3), TODAY));

        verify(accrualService).reconcileLateFeesOfInvoice(7L, TODAY);
    }

    @Test
    void listenerWithProcessedEffectGuardDoesNotClaimTheEventWhenBillingIsOffOrMirror() {
        for (BillingMode mode : List.of(BillingMode.OFF, BillingMode.MIRROR)) {
            ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
            LateFeeWithdrawalCorrectionService correctionService = mock(LateFeeWithdrawalCorrectionService.class);
            EnrollmentWithdrawnForBillingEvent event = withdrawalEvent();

            guarded(new LateFeeCorrectionOnWithdrawalListener(correctionService, effectGuard), mode).correctLateFeesOfWithdrawnEnrollment(event);

            verifyNoInteractions(effectGuard, correctionService);
        }
    }

    @Test
    void listenerWithProcessedEffectGuardClaimsTheEventWhenBillingIsFull() {
        ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
        LateFeeWithdrawalCorrectionService correctionService = mock(LateFeeWithdrawalCorrectionService.class);
        EnrollmentWithdrawnForBillingEvent event = withdrawalEvent();

        guarded(new LateFeeCorrectionOnWithdrawalListener(correctionService, effectGuard), BillingMode.FULL).correctLateFeesOfWithdrawnEnrollment(event);

        verify(effectGuard).runEffectOnce(any(), anyString(), any());
    }

    @Test
    void plainEventListenerDoesNotTouchRepositoriesWhenBillingIsOffOrMirror() {
        for (BillingMode mode : List.of(BillingMode.OFF, BillingMode.MIRROR)) {
            AppliedStudentDiscountRepository applied = mock(AppliedStudentDiscountRepository.class);
            StudentDiscountRepository discounts = mock(StudentDiscountRepository.class);
            EnrollmentDiscountLock lock = mock(EnrollmentDiscountLock.class);

            guarded(new DiscountConsumptionService(applied, discounts, lock, CLOCK), mode)
                    .releaseDiscountConsumption(new InvoiceCancelledEvent(9L, TODAY));

            verifyNoInteractions(applied, discounts, lock);
        }
    }

    @Test
    void plainEventListenerReleasesDiscountConsumptionWhenBillingIsFull() {
        AppliedStudentDiscountRepository applied = mock(AppliedStudentDiscountRepository.class);
        StudentDiscountRepository discounts = mock(StudentDiscountRepository.class);
        EnrollmentDiscountLock lock = mock(EnrollmentDiscountLock.class);

        guarded(new DiscountConsumptionService(applied, discounts, lock, CLOCK), BillingMode.FULL)
                .releaseDiscountConsumption(new InvoiceCancelledEvent(9L, TODAY));

        assertThat(Mockito.mockingDetails(applied).getInvocations().size() + Mockito.mockingDetails(discounts).getInvocations().size()).isPositive();
    }

    private static EnrollmentWithdrawnForBillingEvent withdrawalEvent() {
        return new EnrollmentWithdrawnForBillingEvent(UUID.randomUUID(), "c", Instant.parse("2027-03-10T12:00:00Z"), 1L, 2L, 3L, TODAY, TODAY, "WITHDRAWAL");
    }
}
