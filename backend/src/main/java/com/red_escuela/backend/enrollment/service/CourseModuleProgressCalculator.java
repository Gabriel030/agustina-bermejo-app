package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

final class CourseModuleProgressCalculator {

    private CourseModuleProgressCalculator() {
    }

    static Optional<Percentage> calculateApprovedModulesPercentage(Set<Long> activeModuleIds, Collection<Long> approvedModuleIds) {
        long approvedActiveModules = activeModuleIds.stream().filter(approvedModuleIds::contains).count();
        return Percentage.ratio(approvedActiveModules, activeModuleIds.size());
    }
}
