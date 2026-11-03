package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.dto.GrantEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionAlreadyExistsException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionAlreadyRevokedException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionNotFoundException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionNotNeededException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTerms;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTermsLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentFeeExemptionServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final String REASON = "Beca aprobada por la direccion";

    @Mock
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Mock
    private ProgramYearBillingTemplateRepository templateRepository;
    @Mock
    private YearEnrollmentFeeTermsLookup termsLookup;
    @Mock
    private EnrollmentFeeExemptionRepository exemptionRepository;
    @Mock
    private CurrentPersonService currentPersonService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GrantEnrollmentFeeExemptionService grantService;
    private RevokeEnrollmentFeeExemptionService revokeService;
    private ProgramEnrollment enrollment;
    private ProgramYearBillingTemplate template;
    private Person director;

    @BeforeEach
    void setUp() {
        grantService = new GrantEnrollmentFeeExemptionService(programEnrollmentRepository, templateRepository, termsLookup,
                exemptionRepository, currentPersonService, eventPublisher, CLOCK);
        revokeService = new RevokeEnrollmentFeeExemptionService(exemptionRepository, currentPersonService, eventPublisher, CLOCK);
        Person studentPerson = new Person();
        studentPerson.setId(50L);
        Student student = new Student();
        student.setPerson(studentPerson);
        Program program = new Program();
        program.setId(7L);
        enrollment = new ProgramEnrollment();
        enrollment.setId(10L);
        enrollment.setProgram(program);
        enrollment.setStudent(student);
        Term term = new Term();
        term.setId(3L);
        template = new ProgramYearBillingTemplate();
        template.setId(20L);
        template.setProgram(program);
        template.setTerm(term);
        director = new Person();
        director.setId(99L);
    }

    private GrantEnrollmentFeeExemptionRequest request(int slot) {
        return new GrantEnrollmentFeeExemptionRequest(20L, slot, "  " + REASON + "  ");
    }

    private void givenEnrollmentAndTemplate() {
        when(programEnrollmentRepository.findById(10L)).thenReturn(Optional.of(enrollment));
        when(templateRepository.findById(20L)).thenReturn(Optional.of(template));
    }

    private void givenTerms(int feeCount, boolean blocksEnrollment) {
        when(termsLookup.requireEnrollmentFeeTermsEffectiveOn(7L, 3L, LocalDate.of(2026, 3, 10)))
                .thenReturn(new YearEnrollmentFeeTerms(feeCount, blocksEnrollment, false, false));
    }

    private EnrollmentFeeExemption activeExemption() {
        EnrollmentFeeExemption exemption = new EnrollmentFeeExemption();
        exemption.setId(5L);
        exemption.setProgramEnrollment(enrollment);
        exemption.setTemplate(template);
        exemption.setSlot(1);
        exemption.setReason(REASON);
        exemption.setGrantedBy(director);
        exemption.setGrantedAt(NOW.minusDays(2));
        return exemption;
    }

    @Test
    void grantSavesExemptionWithTrimmedReasonGrantorAndClockTimestamp() {
        givenEnrollmentAndTemplate();
        givenTerms(2, true);
        when(exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(10L, 20L, 2)).thenReturn(Optional.empty());
        when(currentPersonService.resolveCurrentPerson()).thenReturn(director);
        when(exemptionRepository.saveAndFlush(any(EnrollmentFeeExemption.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentFeeExemption saved = grantService.grant(10L, request(2));

        assertThat(saved.getReason()).isEqualTo(REASON);
        assertThat(saved.getSlot()).isEqualTo(2);
        assertThat(saved.getGrantedBy()).isSameAs(director);
        assertThat(saved.getGrantedAt()).isEqualTo(NOW);
        assertThat(saved.isActive()).isTrue();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void grantFailsWhenProgramEnrollmentDoesNotExist() {
        when(programEnrollmentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> grantService.grant(10L, request(1))).isInstanceOf(ProgramEnrollmentNotFoundException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantFailsWhenTemplateDoesNotExist() {
        when(programEnrollmentRepository.findById(10L)).thenReturn(Optional.of(enrollment));
        when(templateRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> grantService.grant(10L, request(1))).isInstanceOf(ProgramYearBillingTemplateNotFoundException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantFailsWhenTemplateBelongsToAnotherProgram() {
        Program otherProgram = new Program();
        otherProgram.setId(8L);
        template.setProgram(otherProgram);
        givenEnrollmentAndTemplate();

        assertThatThrownBy(() -> grantService.grant(10L, request(1))).isInstanceOf(ProgramYearBillingTemplateMismatchException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantFailsWhenSlotExceedsEnrollmentFeesOfTheTerm() {
        givenEnrollmentAndTemplate();
        givenTerms(1, true);

        assertThatThrownBy(() -> grantService.grant(10L, request(2)))
                .isInstanceOf(InvalidEnrollmentFeeConfigurationException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantFailsWhenTermDoesNotBlockAnyProcedure() {
        givenEnrollmentAndTemplate();
        givenTerms(2, false);

        assertThatThrownBy(() -> grantService.grant(10L, request(1))).isInstanceOf(EnrollmentFeeExemptionNotNeededException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantFailsWhenAnActiveExemptionAlreadyExistsForTheSameSlot() {
        givenEnrollmentAndTemplate();
        givenTerms(2, true);
        when(exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(10L, 20L, 1))
                .thenReturn(Optional.of(activeExemption()));

        assertThatThrownBy(() -> grantService.grant(10L, request(1))).isInstanceOf(EnrollmentFeeExemptionAlreadyExistsException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantTranslatesUniqueConstraintRaceIntoAlreadyExists() {
        givenEnrollmentAndTemplate();
        givenTerms(2, true);
        when(exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(10L, 20L, 1)).thenReturn(Optional.empty());
        when(currentPersonService.resolveCurrentPerson()).thenReturn(director);
        when(exemptionRepository.saveAndFlush(any(EnrollmentFeeExemption.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> grantService.grant(10L, request(1))).isInstanceOf(EnrollmentFeeExemptionAlreadyExistsException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void revokeMarksExemptionRevokedWithActorTimestampAndNotes() {
        EnrollmentFeeExemption exemption = activeExemption();
        when(exemptionRepository.findById(5L)).thenReturn(Optional.of(exemption));
        when(currentPersonService.resolveCurrentPerson()).thenReturn(director);

        revokeService.revoke(5L, "Se regularizo el pago");

        assertThat(exemption.isActive()).isFalse();
        assertThat(exemption.getRevokedBy()).isSameAs(director);
        assertThat(exemption.getRevokedAt()).isEqualTo(NOW);
        assertThat(exemption.getRevocationNotes()).isEqualTo("Se regularizo el pago");
        verify(exemptionRepository).saveAndFlush(exemption);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void revokeFailsWhenExemptionDoesNotExist() {
        when(exemptionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> revokeService.revoke(5L, null)).isInstanceOf(EnrollmentFeeExemptionNotFoundException.class);
    }

    @Test
    void revokeFailsWhenExemptionWasAlreadyRevoked() {
        EnrollmentFeeExemption exemption = activeExemption();
        exemption.setRevokedAt(NOW.minusDays(1));
        when(exemptionRepository.findById(5L)).thenReturn(Optional.of(exemption));
        when(currentPersonService.resolveCurrentPerson()).thenReturn(director);

        assertThatThrownBy(() -> revokeService.revoke(5L, null)).isInstanceOf(EnrollmentFeeExemptionAlreadyRevokedException.class);
        verify(exemptionRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void revokeTranslatesConcurrentRevocationIntoAlreadyRevoked() {
        EnrollmentFeeExemption exemption = activeExemption();
        when(exemptionRepository.findById(5L)).thenReturn(Optional.of(exemption));
        when(currentPersonService.resolveCurrentPerson()).thenReturn(director);
        when(exemptionRepository.saveAndFlush(exemption)).thenThrow(new ObjectOptimisticLockingFailureException(EnrollmentFeeExemption.class, 5L));

        assertThatThrownBy(() -> revokeService.revoke(5L, null)).isInstanceOf(EnrollmentFeeExemptionAlreadyRevokedException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
