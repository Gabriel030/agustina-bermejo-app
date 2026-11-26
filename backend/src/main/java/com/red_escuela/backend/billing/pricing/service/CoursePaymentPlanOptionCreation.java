package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoursePaymentPlanOptionCreation {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final PaymentPlanOptionBuilder paymentPlanOptionBuilder;

    public CoursePaymentPlanOptionCreation(PaymentPlanOptionRepository paymentPlanOptionRepository, AcademicReferenceFinder academicReferenceFinder,
                                         PaymentPlanOptionBuilder paymentPlanOptionBuilder) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.academicReferenceFinder = academicReferenceFinder;
        this.paymentPlanOptionBuilder = paymentPlanOptionBuilder;
    }

    @Transactional
    public PaymentPlanOption create(Long courseId, CreatePaymentPlanOptionRequest request) {
        Course course = academicReferenceFinder.findCourse(courseId);
        PaymentPlanOption option = paymentPlanOptionBuilder.build(request, course.getCurrency());
        option.setCourse(course);
        return paymentPlanOptionRepository.save(option);
    }
}
