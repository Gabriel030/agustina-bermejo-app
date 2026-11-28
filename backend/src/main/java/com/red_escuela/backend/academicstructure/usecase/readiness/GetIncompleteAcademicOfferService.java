package com.red_escuela.backend.academicstructure.usecase.readiness;

import com.red_escuela.backend.academicstructure.dto.IncompleteAcademicOfferResponse;
import com.red_escuela.backend.academicstructure.service.IncompleteAcademicOfferFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetIncompleteAcademicOfferService implements GetIncompleteAcademicOfferUseCase {

    private final IncompleteAcademicOfferFinder finder;

    public GetIncompleteAcademicOfferService(IncompleteAcademicOfferFinder finder) {
        this.finder = finder;
    }

    @Override
    public IncompleteAcademicOfferResponse getIncompleteAcademicOffer() {
        return finder.find();
    }
}
