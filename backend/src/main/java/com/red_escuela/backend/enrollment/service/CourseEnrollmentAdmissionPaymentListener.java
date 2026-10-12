package com.red_escuela.backend.enrollment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.events.CourseEnrollmentConfirmedByAdmissionPaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CourseEnrollmentAdmissionPaymentListener {
    private static final Logger log = LoggerFactory.getLogger(CourseEnrollmentAdmissionPaymentListener.class);

    private final CourseEnrollmentApplicationRepository applicationRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public CourseEnrollmentAdmissionPaymentListener(CourseEnrollmentApplicationRepository applicationRepository,
                                                    CourseEnrollmentService courseEnrollmentService, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @ApplicationModuleListener
    public void confirmCourseEnrollmentOfPaidAdmission(AdmissionInvoicePaidEvent event) {
        List<CourseEnrollmentApplication> pending = applicationRepository.findLockedPendingByInvoiceId(event.invoiceId());
        if (pending.isEmpty()) {
            return;
        }
        CourseEnrollmentApplication application = pending.get(0);
        CourseEnrollment enrollment = courseEnrollmentService.enrollSelfService(application.getStudent().getId(),
                new CreateCourseEnrollmentRequest(application.getCourse().getId(), deserializeOverrides(application.getSectionOverrides())));
        application.setCourseEnrollment(enrollment);
        applicationRepository.save(application);
        eventPublisher.publishEvent(CourseEnrollmentConfirmedByAdmissionPaymentEvent.confirmedFor(event.invoiceId(), enrollment.getId()));
        log.info("Inscripción a curso confirmada por el pago de la factura de admisión: solicitud={}, inscripcion={}, factura={}",
                application.getId(), enrollment.getId(), event.invoiceId());
    }

    private Map<Long, Long> deserializeOverrides(String sectionOverridesJson) {
        if (sectionOverridesJson == null || sectionOverridesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(sectionOverridesJson, new TypeReference<Map<Long, Long>>() {
            });
        } catch (JsonProcessingException e) {
            throw new InvalidSectionOverridesException(sectionOverridesJson, e);
        }
    }
}
