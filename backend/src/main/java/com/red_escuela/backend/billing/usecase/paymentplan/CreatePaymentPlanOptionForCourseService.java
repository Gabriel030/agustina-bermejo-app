package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionBuilder;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreatePaymentPlanOptionForCourseService implements CreatePaymentPlanOptionForCourseUseCase {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final CourseRepository courseRepository;
    private final PaymentPlanOptionBuilder paymentPlanOptionBuilder;

    public CreatePaymentPlanOptionForCourseService(PaymentPlanOptionRepository paymentPlanOptionRepository, CourseRepository courseRepository,
                                                     PaymentPlanOptionBuilder paymentPlanOptionBuilder) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.courseRepository = courseRepository;
        this.paymentPlanOptionBuilder = paymentPlanOptionBuilder;
    }

    @Override
    @Transactional
    public PaymentPlanOption create(Long courseId, CreatePaymentPlanOptionRequest request) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        PaymentPlanOption option = paymentPlanOptionBuilder.build(request);
        option.setCourse(course);
        return paymentPlanOptionRepository.save(option);
    }
}
