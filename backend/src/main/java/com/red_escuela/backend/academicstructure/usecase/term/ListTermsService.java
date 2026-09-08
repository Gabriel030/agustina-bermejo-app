package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListTermsService implements ListTermsUseCase {

    private final TermRepository termRepository;

    public ListTermsService(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    @Override
    public List<Term> list(StatusType status) {
        return status == null ? termRepository.findAll() : termRepository.findByStatus(status);
    }
}
