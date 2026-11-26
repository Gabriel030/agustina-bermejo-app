package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SectionEvaluationConfigResponseAssembler {

    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionEvaluationSchemeLoader schemeLoader;
    private final SectionEvaluationConfigRepository configRepository;

    public SectionEvaluationConfigResponseAssembler(SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                    SectionEvaluationSchemeLoader schemeLoader,
                                                    SectionEvaluationConfigRepository configRepository) {
        this.instructorAccess = instructorAccess;
        this.schemeLoader = schemeLoader;
        this.configRepository = configRepository;
    }

    public SectionEvaluationConfigResponse assembleForStaff(SectionEvaluationConfig detachedConfig) {
        SectionEvaluationConfig config = reload(detachedConfig);
        return SectionEvaluationConfigResponse.from(
                config, schemeLoader.load(config.getSubjectSection()), instructorAccess.listInstructors(config.getSubjectSection().getId()));
    }

    public SectionEvaluationConfigResponse assembleForInstructor(SectionEvaluationConfig detachedConfig, Long instructorId) {
        SectionEvaluationConfig config = reload(detachedConfig);
        Long sectionId = config.getSubjectSection().getId();
        boolean isLead = instructorAccess.isLeadInstructor(sectionId, instructorId);
        return SectionEvaluationConfigResponse.from(
                config, schemeLoader.load(config.getSubjectSection()), instructorAccess.listInstructors(sectionId),
                isLead && config.isEditable(), isLead && config.canBeSubmitted(ApplicationTime.now()));
    }

    private SectionEvaluationConfig reload(SectionEvaluationConfig config) {
        return configRepository.findById(config.getId()).orElse(config);
    }
}
