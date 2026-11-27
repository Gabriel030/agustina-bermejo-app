package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.SectionRef;
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
    private final SectionEvaluationConfigRepository configRepository;

    public SectionEvaluationConfigResponseAssembler(SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                    SectionEvaluationConfigRepository configRepository) {
        this.instructorAccess = instructorAccess;
        this.configRepository = configRepository;
    }

    public SectionEvaluationConfigResponse assembleForStaff(SectionEvaluationConfig detachedConfig) {
        SectionEvaluationConfig config = reload(detachedConfig);
        return SectionEvaluationConfigResponse.from(config, instructorAccess.listInstructors(SectionEvaluationConfigOwner.sectionRefOf(config)));
    }

    public SectionEvaluationConfigResponse assembleForInstructor(SectionEvaluationConfig detachedConfig, Long instructorId) {
        SectionEvaluationConfig config = reload(detachedConfig);
        SectionRef section = SectionEvaluationConfigOwner.sectionRefOf(config);
        boolean isLead = instructorAccess.isLeadInstructor(section, instructorId);
        return SectionEvaluationConfigResponse.from(
                config, instructorAccess.listInstructors(section),
                isLead && config.isEditable(), isLead && config.canBeSubmitted(ApplicationTime.now()));
    }

    private SectionEvaluationConfig reload(SectionEvaluationConfig config) {
        return configRepository.findById(config.getId()).orElse(config);
    }
}
