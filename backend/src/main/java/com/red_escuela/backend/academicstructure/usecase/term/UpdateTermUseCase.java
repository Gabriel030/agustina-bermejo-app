package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;

public interface UpdateTermUseCase {
    Term update(UpdateTermCommand command);
}
