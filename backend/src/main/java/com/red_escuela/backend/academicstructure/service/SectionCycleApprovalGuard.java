package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionModificationCycleClosedException;
import org.springframework.stereotype.Component;

@Component
public class SectionCycleApprovalGuard {

    public void ensureCycleIsApproved(SubjectSection section) {
        var cycle = section.getAcademicOfferingCycle();
        if (cycle == null || cycle.getStatus() != AcademicOfferingCycleStatus.APPROVED) {
            throw new SectionModificationCycleClosedException(section.getId());
        }
    }
}
