package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationFinder;
import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityReason;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStep;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStepKind;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ExamCertificateProcedureEligibility implements ProcedureEligibilityStrategy {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OwnExamRegistrationFinder registrationFinder;
    private final ExamCertificateIssuer issuer;
    private final DailyIssuanceLimit dailyIssuanceLimit;

    public ExamCertificateProcedureEligibility(OwnExamRegistrationFinder registrationFinder, ExamCertificateIssuer issuer,
                                               DailyIssuanceLimit dailyIssuanceLimit) {
        this.registrationFinder = registrationFinder;
        this.issuer = issuer;
        this.dailyIssuanceLimit = dailyIssuanceLimit;
    }

    @Override
    public StudentProcedureType type() {
        return StudentProcedureType.EXAM_CERTIFICATE;
    }

    @Override
    public ProcedureEligibilityResponse eligibilityOf(Long studentId, Long enrollmentId, Long registrationId) {
        List<OwnExamRegistrationView> attended = registrationFinder.attendedRegistrationsOf(studentId);
        if (registrationId == null) {
            return attended.isEmpty() ? ineligible(null, attendanceNotRecorded()) : eligible(null);
        }
        OwnExamRegistrationView selected = attended.stream()
                .filter(view -> view.registrationId().equals(registrationId))
                .findFirst()
                .orElseThrow(ProcedureDocumentNotFoundException::new);
        String summary = selected.subjectName() + " - " + selected.examDateTime().toLocalDate().format(DATE_FORMAT);
        boolean reusable = issuer.currentValidOf(selected.registrationId()).isPresent();
        if (!reusable && dailyIssuanceLimit.isReachedForExamCertificate(studentId)) {
            return ineligible(summary, new ProcedureIneligibilityReason(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED,
                    "Ya generaste hoy una constancia de este tipo",
                    new ProcedureNextStep(ProcedureNextStepKind.TRY_AGAIN_LATER, "Probá mañana", null)));
        }
        return eligible(summary);
    }

    private ProcedureEligibilityResponse eligible(String summary) {
        return new ProcedureEligibilityResponse(StudentProcedureType.EXAM_CERTIFICATE, true, null, summary, List.of());
    }

    private ProcedureEligibilityResponse ineligible(String summary, ProcedureIneligibilityReason reason) {
        return new ProcedureEligibilityResponse(StudentProcedureType.EXAM_CERTIFICATE, false, null, summary, List.of(reason));
    }

    private ProcedureIneligibilityReason attendanceNotRecorded() {
        return new ProcedureIneligibilityReason(ProcedureIneligibilityCode.EXAM_ATTENDANCE_NOT_RECORDED,
                "Todavía no tenés un examen rendido con presencia registrada",
                new ProcedureNextStep(ProcedureNextStepKind.TRY_AGAIN_LATER, "Volvé a revisar más tarde", null));
    }
}
