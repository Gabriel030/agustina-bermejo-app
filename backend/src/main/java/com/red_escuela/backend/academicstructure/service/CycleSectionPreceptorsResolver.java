package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionPendingAttendanceRequestsPort;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse.PreceptorInfo;
import com.red_escuela.backend.identity.api.SectionPreceptorFinder;
import com.red_escuela.backend.identity.api.SectionPreceptorView;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CycleSectionPreceptorsResolver {

    private final SectionPreceptorFinder sectionPreceptorFinder;
    private final SectionPendingAttendanceRequestsPort pendingAttendanceRequestsPort;

    public CycleSectionPreceptorsResolver(SectionPreceptorFinder sectionPreceptorFinder,
                                          SectionPendingAttendanceRequestsPort pendingAttendanceRequestsPort) {
        this.sectionPreceptorFinder = sectionPreceptorFinder;
        this.pendingAttendanceRequestsPort = pendingAttendanceRequestsPort;
    }

    public Map<Long, List<PreceptorInfo>> resolveForSubjectSections(Collection<Long> subjectSectionIds) {
        Map<Long, List<SectionPreceptorView>> preceptors = sectionPreceptorFinder.findBySubjectSectionIds(subjectSectionIds);
        if (preceptors.isEmpty()) {
            return Map.of();
        }
        return toInfos(preceptors, pendingAttendanceRequestsPort.countPendingBySubjectSectionIds(preceptors.keySet()));
    }

    public Map<Long, List<PreceptorInfo>> resolveForCourseSections(Collection<Long> courseSectionIds) {
        Map<Long, List<SectionPreceptorView>> preceptors = sectionPreceptorFinder.findByCourseSectionIds(courseSectionIds);
        if (preceptors.isEmpty()) {
            return Map.of();
        }
        return toInfos(preceptors, pendingAttendanceRequestsPort.countPendingByCourseSectionIds(preceptors.keySet()));
    }

    private Map<Long, List<PreceptorInfo>> toInfos(Map<Long, List<SectionPreceptorView>> preceptors,
                                                   Map<Long, Long> pendingBySection) {
        return preceptors.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream()
                        .map(view -> new PreceptorInfo(view.assignmentId(), view.personId(), view.personName(),
                                pendingBySection.getOrDefault(entry.getKey(), 0L)))
                        .toList()));
    }
}
