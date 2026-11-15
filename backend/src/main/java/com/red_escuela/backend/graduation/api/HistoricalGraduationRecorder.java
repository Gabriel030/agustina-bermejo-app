package com.red_escuela.backend.graduation.api;

import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalGraduationRecorder {
    private static final LocalTime HISTORICAL_TIME = LocalTime.NOON;

    private final GraduationRequestRepository graduationRequestRepository;
    private final HistoricalEnrollmentRecorder enrollmentRecorder;
    private final EntityManager entityManager;

    public HistoricalGraduationRecorder(GraduationRequestRepository graduationRequestRepository,
            HistoricalEnrollmentRecorder enrollmentRecorder, EntityManager entityManager) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.enrollmentRecorder = enrollmentRecorder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean graduationExists(DocumentType type, String number, String programName) {
        return enrollmentRecorder.findProgramEnrollmentId(type, number, programName)
                .map(enrollmentId -> graduationRequestRepository.existsByProgramEnrollmentIdAndStatusNot(enrollmentId,
                        GraduationRequestStatus.REJECTED))
                .orElse(false);
    }

    @Transactional
    public Long recordGraduation(HistoricalGraduation historical) {
        Long enrollmentId = enrollmentRecorder.requireCompletedProgramEnrollmentId(historical.documentType(),
                historical.documentNumber(), historical.programName(), historical.graduationDate());
        GraduationRequest request = new GraduationRequest();
        request.setProgramEnrollment(entityManager.getReference(ProgramEnrollment.class, enrollmentId));
        request.setStatus(GraduationRequestStatus.APPROVED);
        request.setRequestedAt(historical.graduationDate().atTime(HISTORICAL_TIME));
        request.setDecidedAt(historical.graduationDate().atTime(HISTORICAL_TIME));
        request.setRegistryNumber(historical.registryNumber());
        request.setLegacyTitleNumber(historical.legacyTitleNumber());
        return graduationRequestRepository.save(request).getId();
    }

    @Transactional
    public void discardGraduation(Long graduationRequestId) {
        graduationRequestRepository.deleteById(graduationRequestId);
        graduationRequestRepository.flush();
    }
}
