package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;

public interface CreateTermUseCase {
    Term create(CreateTermRequest request);
}
