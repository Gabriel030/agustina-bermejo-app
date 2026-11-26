package com.red_escuela.backend.enrollment.service;

import org.springframework.dao.DataIntegrityViolationException;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeExemptionTermsPort;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeExemptionTermsView;
import com.red_escuela.backend.enrollment.dto.GrantEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionAlreadyExistsException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionNotNeededException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class GrantEnrollmentFeeExemptionTransaction {
    private static final Logger log = LoggerFactory.getLogger(GrantEnrollmentFeeExemptionTransaction.class);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final EnrollmentFeeExemptionTermsPort exemptionTermsPort;
    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GrantEnrollmentFeeExemptionTransaction(ProgramEnrollmentRepository programEnrollmentRepository,
                                              EnrollmentFeeExemptionTermsPort exemptionTermsPort,
                                              EnrollmentFeeExemptionRepository exemptionRepository, CurrentPersonService currentPersonService,
                                              ApplicationEventPublisher eventPublisher, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.exemptionTermsPort = exemptionTermsPort;
        this.exemptionRepository = exemptionRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public EnrollmentFeeExemption grant(Long programEnrollmentId, GrantEnrollmentFeeExemptionRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        EnrollmentFeeExemptionTermsView terms = exemptionTermsPort.requireTermsForExemption(request.templateId(), enrollment.getProgram().getId(), request.slot(),
                LocalDate.now(clock));
        if (!terms.blocksAnyProcedure()) {
            throw new EnrollmentFeeExemptionNotNeededException("El año lectivo " + terms.templateId() + " no bloquea trámites por matrícula impaga");
        }
        if (exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(programEnrollmentId, terms.templateId(), request.slot()).isPresent()) {
            throw new EnrollmentFeeExemptionAlreadyExistsException(programEnrollmentId, terms.templateId(), request.slot());
        }
        EnrollmentFeeExemption exemption = new EnrollmentFeeExemption();
        exemption.setProgramEnrollment(enrollment);
        exemption.setTemplateId(terms.templateId());
        exemption.setSlot(request.slot());
        exemption.setReason(request.reason().trim());
        exemption.setGrantedBy(currentPersonService.resolveCurrentPerson());
        exemption.setGrantedAt(LocalDateTime.now(clock));
        EnrollmentFeeExemption saved;
        try {
            saved = exemptionRepository.saveAndFlush(exemption);
        } catch (DataIntegrityViolationException e) {
            throw new EnrollmentFeeExemptionAlreadyExistsException(programEnrollmentId, terms.templateId(), request.slot());
        }
        log.info("Habilitación de matrícula otorgada: habilitacion={}, inscripcion={}, plantilla={}, matricula={}, por={}",
                saved.getId(), programEnrollmentId, terms.templateId(), request.slot(), saved.getGrantedBy().getId());
        eventPublisher.publishEvent(EnrollmentNotificationEvents.exemptionGrantOf(saved));
        return saved;
    }
}
