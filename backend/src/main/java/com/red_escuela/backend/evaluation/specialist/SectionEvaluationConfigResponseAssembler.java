package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class SectionEvaluationConfigResponseAssembler {

    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;

    public SectionEvaluationConfigResponseAssembler(SectionEvaluationConfigInstructorAccessChecker instructorAccess) {
        this.instructorAccess = instructorAccess;
    }

    public SectionEvaluationConfigResponse assembleForStaff(SectionEvaluationConfig config) {
        return SectionEvaluationConfigResponse.from(config, instructorAccess.listInstructors(config.getSubjectSection().getId()));
    }

    public SectionEvaluationConfigResponse assembleForInstructor(SectionEvaluationConfig config, Long instructorId) {
        Long sectionId = config.getSubjectSection().getId();
        boolean isLead = instructorAccess.isLeadInstructor(sectionId, instructorId);
        return SectionEvaluationConfigResponse.from(
                config, instructorAccess.listInstructors(sectionId),
                isLead && config.isEditable(), isLead && config.canBeSubmitted(ApplicationTime.now()));
    }
}
