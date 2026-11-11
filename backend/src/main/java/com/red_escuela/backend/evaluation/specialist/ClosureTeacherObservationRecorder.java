package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyClosedException;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyConfirmedException;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.ClosureStudentNotFoundException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClosureTeacherObservationRecorder {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureStudentRepository closureStudentRepository;

    public ClosureTeacherObservationRecorder(SectionFinder sectionFinder, SectionTeachingFinder sectionTeachingFinder,
            CommissionClosureRepository closureRepository, CommissionClosureStudentRepository closureStudentRepository) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.closureRepository = closureRepository;
        this.closureStudentRepository = closureStudentRepository;
    }

    @Transactional
    public void recordObservation(ClosureSectionKind sectionKind, Long sectionId, Long enrollmentId, Long instructorId, String observation) {
        SectionKind kind = sectionKind.toSectionKind();
        sectionFinder.requireExists(kind, sectionId);
        sectionTeachingFinder.requireTeaches(kind, sectionId, instructorId);
        Long closureId = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .orElseThrow(() -> new ClosureNotAvailableException(sectionId)).getId();
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId).orElseThrow();
        if (closure.getStatus() == ClosureStatus.TEACHER_CONFIRMED) {
            throw new ClosureAlreadyConfirmedException(closureId);
        }
        if (closure.getStatus() == ClosureStatus.CLOSED) {
            throw new ClosureAlreadyClosedException(closureId);
        }
        CommissionClosureStudent row = closureStudentRepository.findByClosureIdAndEnrollmentId(closureId, enrollmentId)
                .orElseThrow(() -> new ClosureStudentNotFoundException(enrollmentId));
        String trimmed = observation == null ? "" : observation.trim();
        row.changeTeacherObservation(trimmed.isEmpty() ? null : trimmed);
        closureStudentRepository.save(row);
    }
}
