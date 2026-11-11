package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.ClosurePrerequisiteResponse;

import java.util.List;

public interface GetClosureStudentPrerequisitesUseCase {

    List<ClosurePrerequisiteResponse> get(GetClosureStudentPrerequisitesCommand command);
}
