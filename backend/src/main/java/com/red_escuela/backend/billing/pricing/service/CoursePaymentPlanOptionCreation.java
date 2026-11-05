package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoursePaymentPlanOptionCreation {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final CourseRepository courseRepository;
    private final PaymentPlanOptionBuilder paymentPlanOptionBuilder;

    public CoursePaymentPlanOptionCreation(PaymentPlanOptionRepository paymentPlanOptionRepository, CourseRepository courseRepository,
                                         PaymentPlanOptionBuilder paymentPlanOptionBuilder) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.courseRepository = courseRepository;
        this.paymentPlanOptionBuilder = paymentPlanOptionBuilder;
    }

    @Transactional
    public PaymentPlanOption create(Long courseId, CreatePaymentPlanOptionRequest request) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        PaymentPlanOption option = paymentPlanOptionBuilder.build(request, course.getCurrency());
        option.setCourse(course);
        return paymentPlanOptionRepository.save(option);
    }
}
