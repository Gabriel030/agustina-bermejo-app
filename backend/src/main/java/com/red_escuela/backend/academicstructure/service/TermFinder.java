package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Service;

@Service
public class TermFinder {

    private final TermRepository termRepository;

    public TermFinder(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    public Term findOrThrow(Long id) {
        return termRepository.findById(id).orElseThrow(() -> new TermNotFoundException(id));
    }
}
