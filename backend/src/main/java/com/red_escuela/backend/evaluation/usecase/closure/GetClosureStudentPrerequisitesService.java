package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.ClosurePrerequisiteResponse;
import com.red_escuela.backend.evaluation.specialist.ClosureStudentPrerequisitesFinder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetClosureStudentPrerequisitesService implements GetClosureStudentPrerequisitesUseCase {

    private final ClosureStudentPrerequisitesFinder prerequisitesFinder;

    public GetClosureStudentPrerequisitesService(ClosureStudentPrerequisitesFinder prerequisitesFinder) {
        this.prerequisitesFinder = prerequisitesFinder;
    }

    @Override
    public List<ClosurePrerequisiteResponse> get(GetClosureStudentPrerequisitesCommand command) {
        return prerequisitesFinder.findPrerequisites(command.closureId(), command.enrollmentId(), command.viewer(), command.instructorId());
    }
}
