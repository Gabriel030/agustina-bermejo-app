package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.exception.GraduationRequestNotFoundException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Service;

@Service
public class GraduationRequestFinder {

    private final GraduationRequestRepository graduationRequestRepository;

    public GraduationRequestFinder(GraduationRequestRepository graduationRequestRepository) {
        this.graduationRequestRepository = graduationRequestRepository;
    }

    public GraduationRequest findOrThrow(Long id) {
        return graduationRequestRepository.findById(id)
                .orElseThrow(() -> new GraduationRequestNotFoundException(id));
    }
}
