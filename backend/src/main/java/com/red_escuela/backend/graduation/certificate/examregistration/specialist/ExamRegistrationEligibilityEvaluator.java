package com.red_escuela.backend.graduation.certificate.examregistration.specialist;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationFinder;
import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings;
import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings.DocumentIssuanceValues;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.OverdueDebtProbe;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityReason;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStep;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStepKind;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class ExamRegistrationEligibilityEvaluator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OwnExamRegistrationFinder registrationFinder;
    private final ExamRegistrationCertificateRepository certificateRepository;
    private final DocumentIssuanceSettings issuanceSettings;
    private final BillingModeGate billingModeGate;
    private final OverdueDebtProbe overdueDebtProbe;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public ExamRegistrationEligibilityEvaluator(OwnExamRegistrationFinder registrationFinder,
                                                ExamRegistrationCertificateRepository certificateRepository,
                                                DocumentIssuanceSettings issuanceSettings, BillingModeGate billingModeGate,
                                                OverdueDebtProbe overdueDebtProbe, CurrentActorFinder currentActorFinder,
                                                Clock clock) {
        this.registrationFinder = registrationFinder;
        this.certificateRepository = certificateRepository;
        this.issuanceSettings = issuanceSettings;
        this.billingModeGate = billingModeGate;
        this.overdueDebtProbe = overdueDebtProbe;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    public ProcedureEligibilityResponse evaluate(Long registrationId) {
        return select(registrationId).eligibility();
    }

    public record ExamRegistrationSelection(OwnExamRegistrationView registration, ProcedureEligibilityResponse eligibility) {
    }

    public ExamRegistrationSelection select(Long registrationId) {
        Long studentId = currentActorFinder.currentStudentId();
        LocalDateTime now = LocalDateTime.now(clock);
        DocumentIssuanceValues values = issuanceSettings.current();
        List<OwnExamRegistrationView> registrations = registrationFinder.activeRegistrationsOf(studentId, now.toLocalDate().atStartOfDay());
        OwnExamRegistrationView selected = registrationId != null
                ? requireOwned(registrations, registrationId)
                : pickDefault(registrations, now.toLocalDate(), values.examRegistrationAdvanceDays());
        if (selected == null) {
            return new ExamRegistrationSelection(null, ineligible(null, reason(ProcedureIneligibilityCode.NOT_REGISTERED_FOR_EXAM,
                    "No estás inscripto/a a una mesa de examen", ProcedureNextStepKind.REGISTER_FOR_EXAM,
                    "Inscribite a una mesa")));
        }
        ProcedureIneligibilityReason blocker = firstBlocker(selected, studentId, now, values);
        String summary = selected.subjectName() + " - " + selected.examDateTime().toLocalDate().format(DATE_FORMAT);
        return new ExamRegistrationSelection(selected, blocker == null
                ? new ProcedureEligibilityResponse(StudentProcedureType.EXAM_REGISTRATION_CERTIFICATE, true, null, summary, List.of())
                : new ProcedureEligibilityResponse(StudentProcedureType.EXAM_REGISTRATION_CERTIFICATE, false, null, summary, List.of(blocker)));
    }

    private OwnExamRegistrationView requireOwned(List<OwnExamRegistrationView> registrations, Long registrationId) {
        return registrations.stream()
                .filter(view -> view.registrationId().equals(registrationId))
                .findFirst()
                .orElseThrow(ProcedureDocumentNotFoundException::new);
    }

    private OwnExamRegistrationView pickDefault(List<OwnExamRegistrationView> registrations, LocalDate today, int advanceDays) {
        return registrations.stream()
                .filter(view -> !today.isBefore(windowOpensOn(view, advanceDays)))
                .findFirst()
                .orElse(registrations.isEmpty() ? null : registrations.get(0));
    }

    private LocalDate windowOpensOn(OwnExamRegistrationView view, int advanceDays) {
        return view.examDateTime().toLocalDate().minusDays(advanceDays);
    }

    private ProcedureIneligibilityReason firstBlocker(OwnExamRegistrationView view, Long studentId, LocalDateTime now,
                                                      DocumentIssuanceValues values) {
        LocalDate opensOn = windowOpensOn(view, values.examRegistrationAdvanceDays());
        if (now.toLocalDate().isBefore(opensOn)) {
            return reason(ProcedureIneligibilityCode.NO_OPEN_ENROLLMENT_WINDOW,
                    "Se habilita " + values.examRegistrationAdvanceDays() + " días antes: desde el " + opensOn.format(DATE_FORMAT),
                    ProcedureNextStepKind.WAIT_FOR_ENROLLMENT_WINDOW, "Esperá a que se habilite");
        }
        ProcedureIneligibilityReason payments = paymentsBlocker(studentId);
        if (payments != null) {
            return payments;
        }
        return limitBlocker(view, studentId, now, values.dailyIssuanceLimitPerType());
    }

    private ProcedureIneligibilityReason paymentsBlocker(Long studentId) {
        BillingMode mode = billingModeGate.currentMode();
        if (mode == BillingMode.OFF) {
            return null;
        }
        if (mode == BillingMode.MIRROR) {
            return reason(ProcedureIneligibilityCode.PENDING_PAYMENTS,
                    "No podemos verificar tus pagos automáticamente: comunicate con Administración",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Comunicate con Administración");
        }
        return overdueDebtProbe.hasOverdueDebt(studentId)
                ? reason(ProcedureIneligibilityCode.PENDING_PAYMENTS, "Para generar este documento tenés que estar al día con los pagos",
                ProcedureNextStepKind.GO_TO_PAYMENTS, "Ir a pagos")
                : null;
    }

    private ProcedureIneligibilityReason limitBlocker(OwnExamRegistrationView view, Long studentId, LocalDateTime now, int dailyLimit) {
        boolean redownloadable = certificateRepository.findFirstByStudentIdAndRegistrationIdAndStatusOrderByIssuedAtDesc(
                studentId, view.registrationId(), DocumentValidityStatus.VALID).filter(current -> current.isValidAt(now)).isPresent();
        if (redownloadable) {
            return null;
        }
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        long issuedToday = certificateRepository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
                studentId, dayStart, dayStart.plusDays(1));
        return issuedToday >= dailyLimit
                ? reason(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED, "Ya generaste hoy una constancia de este tipo",
                ProcedureNextStepKind.TRY_AGAIN_LATER, "Probá mañana")
                : null;
    }

    private ProcedureEligibilityResponse ineligible(String summary, ProcedureIneligibilityReason reason) {
        return new ProcedureEligibilityResponse(StudentProcedureType.EXAM_REGISTRATION_CERTIFICATE, false, null, summary, List.of(reason));
    }

    private ProcedureIneligibilityReason reason(ProcedureIneligibilityCode code, String message, ProcedureNextStepKind kind, String label) {
        return new ProcedureIneligibilityReason(code, message, new ProcedureNextStep(kind, label, null));
    }
}
