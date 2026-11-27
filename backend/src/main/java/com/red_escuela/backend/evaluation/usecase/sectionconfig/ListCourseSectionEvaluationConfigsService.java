package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.CourseSectionEvaluationConfigStatusLister;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListCourseSectionEvaluationConfigsService implements ListCourseSectionEvaluationConfigsUseCase {

    private final CourseSectionEvaluationConfigStatusLister statusLister;

    public ListCourseSectionEvaluationConfigsService(CourseSectionEvaluationConfigStatusLister statusLister) {
        this.statusLister = statusLister;
    }

    @Override
    public List<SectionEvaluationConfigResponse> list(String statusName) {
        return statusLister.listByStatusName(statusName);
    }
}
