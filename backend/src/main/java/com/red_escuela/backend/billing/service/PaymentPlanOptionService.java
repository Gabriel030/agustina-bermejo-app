package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.red_escuela.backend.identity.service.CurrentPersonService;

@Service
public class PaymentPlanOptionService {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final CurrentPersonService currentPersonService;

    public PaymentPlanOptionService(PaymentPlanOptionRepository paymentPlanOptionRepository, CourseRepository courseRepository,
                                     ProgramRepository programRepository, CurrentPersonService currentPersonService) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.courseRepository = courseRepository;
        this.programRepository = programRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public PaymentPlanOption crearParaCurso(Long courseId, CreatePaymentPlanOptionRequest request) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        PaymentPlanOption option = build(request);
        option.setCourse(course);
        return paymentPlanOptionRepository.save(option);
    }

    @Transactional
    public PaymentPlanOption crearParaCarrera(Long programId, CreatePaymentPlanOptionRequest request) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        PaymentPlanOption option = build(request);
        option.setProgram(program);
        return paymentPlanOptionRepository.save(option);
    }

    public PaymentPlanOption findOrThrow(Long id) {
        return paymentPlanOptionRepository.findById(id)
                .orElseThrow(() -> new PaymentPlanOptionNotFoundException(id));
    }

    private PaymentPlanOption build(CreatePaymentPlanOptionRequest request) {
        validateUpfrontWindow(request);
        Person actor = currentPersonService.resolveCurrentPerson();
        PaymentPlanOption option = new PaymentPlanOption();
        option.setDescription(request.description());
        option.setCadence(request.cadence());
        option.setUpfrontDiscount(request.upfrontDiscount() == null ? null : request.upfrontDiscount().toValue());
        option.setUpfrontWindowEnabled(request.upfrontWindowEnabled());
        option.setUpfrontWindowStart(request.upfrontWindowStart());
        option.setUpfrontWindowEnd(request.upfrontWindowEnd());
        option.setCreatedBy(actor);
        return option;
    }

    // Sin esto, una PaymentPlanOption con upfrontWindowEnabled=true pero start/end nulos o mal
    // ordenados recién explotaba con NullPointerException cuando un alumno intentaba seleccionar
    // el plan (PaymentPlanSelectionService.validateUpfrontWindow) — 500 en vez de rechazar la
    // creación con 400 (hallazgo de auditoría 2026-09-04, mismo criterio que ya existe para la
    // ventana de admisión de ProgramCohort).
    private void validateUpfrontWindow(CreatePaymentPlanOptionRequest request) {
        if (!request.upfrontWindowEnabled()) {
            return;
        }
        if (request.upfrontWindowStart() == null || request.upfrontWindowEnd() == null) {
            throw new InvalidWindowConfigurationException(
                    "upfrontWindowEnabled=true exige upfrontWindowStart y upfrontWindowEnd no nulos");
        }
        if (request.upfrontWindowStart().isAfter(request.upfrontWindowEnd())) {
            throw new InvalidWindowConfigurationException(
                    "upfrontWindowStart no puede ser posterior a upfrontWindowEnd");
        }
    }
}
