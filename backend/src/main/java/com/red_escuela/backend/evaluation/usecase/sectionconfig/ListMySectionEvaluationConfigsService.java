package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDueDaysReader;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMySectionEvaluationConfigsService implements ListMySectionEvaluationConfigsUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;
    private final SectionEvaluationConfigDueDaysReader dueDaysReader;

    public ListMySectionEvaluationConfigsService(SectionEvaluationConfigRepository configRepository,
                                                 SectionEvaluationConfigResponseAssembler responseAssembler,
                                                 SectionEvaluationConfigDueDaysReader dueDaysReader) {
        this.configRepository = configRepository;
        this.responseAssembler = responseAssembler;
        this.dueDaysReader = dueDaysReader;
    }

    @Override
    public List<SectionEvaluationConfigResponse> list(Long instructorId) {
        int dueDays = dueDaysReader.read();
        return configRepository.findBySubjectSection_Instructors_InstructorId(instructorId).stream()
                .map(config -> responseAssembler.assembleForInstructor(config, instructorId).withEvaluationConfigDueDays(dueDays))
                .toList();
    }
}
