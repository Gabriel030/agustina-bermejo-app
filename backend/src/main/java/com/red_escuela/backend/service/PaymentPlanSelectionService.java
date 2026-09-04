package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PaymentPlanCadence;
import com.red_escuela.backend.entity.PaymentPlanOption;
import com.red_escuela.backend.entity.PaymentPlanSelection;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.CoursePaymentPlanAlreadySetException;
import com.red_escuela.backend.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.exception.PaymentPlanOptionMismatchException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.UpfrontWindowClosedException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class PaymentPlanSelectionService {

    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final PaymentPlanOptionService paymentPlanOptionService;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CurrentPersonService currentPersonService;
    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentBillingScheduler programEnrollmentBillingScheduler;
    private final RecurringFeeWindowService recurringFeeWindowService;

    public PaymentPlanSelectionService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, PaymentPlanOptionService paymentPlanOptionService,
                                        CourseEnrollmentRepository courseEnrollmentRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                        CurrentPersonService currentPersonService, CurrentStudentService currentStudentService,
                                        ProgramEnrollmentBillingScheduler programEnrollmentBillingScheduler,
                                        RecurringFeeWindowService recurringFeeWindowService) {
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.paymentPlanOptionService = paymentPlanOptionService;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.currentPersonService = currentPersonService;
        this.currentStudentService = currentStudentService;
        this.programEnrollmentBillingScheduler = programEnrollmentBillingScheduler;
        this.recurringFeeWindowService = recurringFeeWindowService;
    }

    // Endpoint compartido (spec: "ADMINISTRATIVO|autoservicio alumno") — Optional.empty() de
    // findCurrentStudent() significa que el actor es staff (Administrativo/Director, sin fila
    // Student propia), que puede operar sobre cualquier matriculación sin restricción.
    private void validarPropiaSiEsAlumno(Long ownerStudentId, Long enrollmentId) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(ownerStudentId)) {
                throw new NotOwnEnrollmentException(student.getId(), enrollmentId);
            }
        });
    }

    @Transactional
    public PaymentPlanSelection seleccionarParaCurso(Long courseEnrollmentId, SelectPaymentPlanRequest request) {
        CourseEnrollment courseEnrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        validarPropiaSiEsAlumno(courseEnrollment.getStudent().getId(), courseEnrollmentId);
        // A course's payment plan is unique and immutable for the life of the enrollment.
        if (paymentPlanSelectionRepository.findByCourseEnrollmentIdAndEffectiveToIsNull(courseEnrollmentId).isPresent()) {
            throw new CoursePaymentPlanAlreadySetException(courseEnrollmentId);
        }
        PaymentPlanOption option = paymentPlanOptionService.findOrThrow(request.paymentPlanOptionId());
        if (option.getCourse() == null || !option.getCourse().getId().equals(courseEnrollment.getCourse().getId())) {
            throw new PaymentPlanOptionMismatchException(option.getId());
        }
        validateUpfrontWindow(option);

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setCourseEnrollment(courseEnrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(YearMonth.now());
        if (option.getCadence() == PaymentPlanCadence.UPFRONT) {
            validateScope(request.scopeMonths(), recurringFeeWindowService.remainingInvoices(courseEnrollment));
            selection.setScopeMonths(request.scopeMonths());
        }
        selection.setCreatedBy(currentPersonService.resolveCurrentPerson());
        return paymentPlanSelectionRepository.save(selection);
    }

    @Transactional
    public PaymentPlanSelection seleccionarParaCarrera(Long programEnrollmentId, SelectPaymentPlanRequest request) {
        ProgramEnrollment programEnrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        validarPropiaSiEsAlumno(programEnrollment.getStudent().getId(), programEnrollmentId);
        PaymentPlanOption option = paymentPlanOptionService.findOrThrow(request.paymentPlanOptionId());
        if (option.getProgram() == null || !option.getProgram().getId().equals(programEnrollment.getProgram().getId())) {
            throw new PaymentPlanOptionMismatchException(option.getId());
        }
        validateUpfrontWindow(option);

        // The very first selection for this enrollment triggers the ENROLLMENT_FEE invoice —
        // matrícula is generated together with choosing how to pay, not on the bare enrollment.
        boolean isFirstSelection = paymentPlanSelectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(programEnrollmentId).isEmpty();

        // Several successive selections are allowed for a program, but never overlapping — close
        // whatever selection is currently open before opening the new one. saveAndFlush (not
        // save) on purpose: Hibernate flushes all pending INSERTs before any pending UPDATEs
        // regardless of call order, so without forcing this UPDATE to hit the DB first, the new
        // selection's INSERT would run while the old row still has effective_to = null and
        // collide with uk_payment_plan_selection_open.
        paymentPlanSelectionRepository.findByProgramEnrollmentIdAndEffectiveToIsNull(programEnrollmentId)
                .ifPresent(previous -> {
                    previous.setEffectiveTo(YearMonth.now().minusMonths(1));
                    paymentPlanSelectionRepository.saveAndFlush(previous);
                });

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setProgramEnrollment(programEnrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(YearMonth.now());
        if (option.getCadence() == PaymentPlanCadence.UPFRONT) {
            validateScope(request.scopeMonths(), recurringFeeWindowService.remainingInvoices(programEnrollment));
            selection.setScopeMonths(request.scopeMonths());
        }
        selection.setCreatedBy(currentPersonService.resolveCurrentPerson());
        PaymentPlanSelection saved = paymentPlanSelectionRepository.save(selection);
        if (isFirstSelection) {
            programEnrollmentBillingScheduler.generarMatricula(programEnrollment);
        }
        return saved;
    }

    private void validateUpfrontWindow(PaymentPlanOption option) {
        if (option.getCadence() != PaymentPlanCadence.UPFRONT) {
            return;
        }
        if (!option.isUpfrontWindowEnabled()) {
            throw new UpfrontWindowClosedException(option.getId());
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(option.getUpfrontWindowStart()) || today.isAfter(option.getUpfrontWindowEnd())) {
            throw new UpfrontWindowClosedException(option.getId());
        }
    }

    private void validateScope(Integer scopeMonths, Integer maxMonths) {
        if (scopeMonths == null || scopeMonths <= 0) {
            throw new InvalidPaymentPlanScopeException("scopeMonths must be a positive number");
        }
        if (maxMonths != null && scopeMonths > maxMonths) {
            throw new InvalidPaymentPlanScopeException("scopeMonths (" + scopeMonths + ") exceeds what remains of the term (" + maxMonths + ")");
        }
    }

}
