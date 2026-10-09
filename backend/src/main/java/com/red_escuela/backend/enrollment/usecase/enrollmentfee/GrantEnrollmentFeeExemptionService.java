package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import org.springframework.dao.DataIntegrityViolationException;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTerms;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTermsLookup;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.dto.GrantEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionAlreadyExistsException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionNotNeededException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.EnrollmentFeeExemptionGrantedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@UseCase
public class GrantEnrollmentFeeExemptionService implements GrantEnrollmentFeeExemptionUseCase {
    private static final Logger log = LoggerFactory.getLogger(GrantEnrollmentFeeExemptionService.class);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearEnrollmentFeeTermsLookup termsLookup;
    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GrantEnrollmentFeeExemptionService(ProgramEnrollmentRepository programEnrollmentRepository,
                                              ProgramYearBillingTemplateRepository templateRepository, YearEnrollmentFeeTermsLookup termsLookup,
                                              EnrollmentFeeExemptionRepository exemptionRepository, CurrentPersonService currentPersonService,
                                              ApplicationEventPublisher eventPublisher, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.templateRepository = templateRepository;
        this.termsLookup = termsLookup;
        this.exemptionRepository = exemptionRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EnrollmentFeeExemption grant(Long programEnrollmentId, GrantEnrollmentFeeExemptionRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        ProgramYearBillingTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(request.templateId()));
        if (!template.getProgram().getId().equals(enrollment.getProgram().getId())) {
            throw new ProgramYearBillingTemplateMismatchException(template.getId());
        }
        YearEnrollmentFeeTerms terms = termsLookup.requireEnrollmentFeeTermsEffectiveOn(template.programId(), template.termId(), LocalDate.now(clock));
        if (request.slot() > terms.enrollmentFeeCount()) {
            throw new InvalidEnrollmentFeeConfigurationException("El año lectivo solo tiene " + terms.enrollmentFeeCount() + " matrícula(s)");
        }
        if (!terms.blocksAnyProcedure()) {
            throw new EnrollmentFeeExemptionNotNeededException("El año lectivo " + template.getId() + " no bloquea trámites por matrícula impaga");
        }
        if (exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(programEnrollmentId, template.getId(), request.slot()).isPresent()) {
            throw new EnrollmentFeeExemptionAlreadyExistsException(programEnrollmentId, template.getId(), request.slot());
        }
        EnrollmentFeeExemption exemption = new EnrollmentFeeExemption();
        exemption.setProgramEnrollment(enrollment);
        exemption.setTemplate(template);
        exemption.setSlot(request.slot());
        exemption.setReason(request.reason().trim());
        exemption.setGrantedBy(currentPersonService.resolveCurrentPerson());
        exemption.setGrantedAt(LocalDateTime.now(clock));
        EnrollmentFeeExemption saved;
        try {
            saved = exemptionRepository.saveAndFlush(exemption);
        } catch (DataIntegrityViolationException e) {
            throw new EnrollmentFeeExemptionAlreadyExistsException(programEnrollmentId, template.getId(), request.slot());
        }
        log.info("Habilitación de matrícula otorgada: habilitacion={}, inscripcion={}, plantilla={}, matricula={}, por={}",
                saved.getId(), programEnrollmentId, template.getId(), request.slot(), saved.getGrantedBy().getId());
        eventPublisher.publishEvent(new EnrollmentFeeExemptionGrantedEvent(saved));
        return saved;
    }
}
