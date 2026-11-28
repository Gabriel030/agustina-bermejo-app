package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.events.ProgramEnrollmentWithdrawnEvent;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProgramEnrollmentExamRegistrationCanceller {

    private final ExamBoardRegistrationRepository repository;

    public ProgramEnrollmentExamRegistrationCanceller(ExamBoardRegistrationRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onProgramEnrollmentWithdrawn(ProgramEnrollmentWithdrawnEvent event) {
        repository.findByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndStatus(
                event.programEnrollmentId(), ExamBoardRegistrationStatus.REGISTERED).forEach(registration -> {
            registration.setStatus(ExamBoardRegistrationStatus.WITHDRAWN);
            repository.save(registration);
        });
    }
}
