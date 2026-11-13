package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.shared.domain.Money;
import java.util.Optional;

public interface GetCourseListPriceUseCase {
    Optional<Money> execute(Long courseId);
}
