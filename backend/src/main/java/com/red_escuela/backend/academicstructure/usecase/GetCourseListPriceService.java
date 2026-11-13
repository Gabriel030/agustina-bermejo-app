package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.service.OfferingListPriceReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Money;
import java.util.Optional;

@UseCase
public class GetCourseListPriceService implements GetCourseListPriceUseCase {

    private final OfferingListPriceReader listPriceReader;

    public GetCourseListPriceService(OfferingListPriceReader listPriceReader) {
        this.listPriceReader = listPriceReader;
    }

    @Override
    public Optional<Money> execute(Long courseId) {
        return listPriceReader.findCourseTuition(courseId);
    }
}
