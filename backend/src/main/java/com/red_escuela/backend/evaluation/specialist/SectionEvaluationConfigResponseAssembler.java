package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class SectionEvaluationConfigResponseAssembler {

    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionEvaluationSchemeLoader schemeLoader;

    public SectionEvaluationConfigResponseAssembler(SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                    SectionEvaluationSchemeLoader schemeLoader) {
        this.instructorAccess = instructorAccess;
        this.schemeLoader = schemeLoader;
    }

    public SectionEvaluationConfigResponse assembleForStaff(SectionEvaluationConfig config) {
        return SectionEvaluationConfigResponse.from(
                config, schemeLoader.load(config.getSubjectSection()), instructorAccess.listInstructors(config.getSubjectSection().getId()));
    }

    public SectionEvaluationConfigResponse assembleForInstructor(SectionEvaluationConfig config, Long instructorId) {
        Long sectionId = config.getSubjectSection().getId();
        boolean isLead = instructorAccess.isLeadInstructor(sectionId, instructorId);
        return SectionEvaluationConfigResponse.from(
                config, schemeLoader.load(config.getSubjectSection()), instructorAccess.listInstructors(sectionId),
                isLead && config.isEditable(), isLead && config.canBeSubmitted(ApplicationTime.now()));
    }
}
