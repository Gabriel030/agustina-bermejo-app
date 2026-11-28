package com.red_escuela.backend.academicstructure.dto;

import java.util.List;

public record IncompleteAcademicOfferResponse(int incompleteCohortCount, int incompleteCourseCount,
                                              List<IncompleteOfferItemResponse> cohorts, List<IncompleteOfferItemResponse> courses) {
}
