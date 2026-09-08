package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.entity.StatusType;

import java.util.List;

public interface ListTermsUseCase {
    List<Term> list(StatusType status);
}
