package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PaymentPlanOption;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.PaymentPlanOptionNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
