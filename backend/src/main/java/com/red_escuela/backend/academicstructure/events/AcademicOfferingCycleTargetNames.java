package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;

final class AcademicOfferingCycleTargetNames {

    private AcademicOfferingCycleTargetNames() {
    }

    static String of(AcademicOfferingCycle cycle) {
        return cycle.getProgram() != null ? cycle.getProgram().getName() : cycle.getCourse().getName();
    }
}
