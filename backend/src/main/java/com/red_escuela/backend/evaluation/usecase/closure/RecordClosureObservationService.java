package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.specialist.ClosureTeacherObservationRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RecordClosureObservationService implements RecordClosureObservationUseCase {

    private final ClosureTeacherObservationRecorder observationRecorder;

    public RecordClosureObservationService(ClosureTeacherObservationRecorder observationRecorder) {
        this.observationRecorder = observationRecorder;
    }

    @Override
    public void record(RecordClosureObservationCommand command) {
        observationRecorder.recordObservation(command.kind(), command.sectionId(), command.enrollmentId(), command.instructorId(), command.observation());
    }
}
