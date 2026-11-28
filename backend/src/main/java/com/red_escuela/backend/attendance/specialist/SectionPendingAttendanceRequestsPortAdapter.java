package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.api.SectionPendingAttendanceRequestsPort;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.SectionRequestCount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class SectionPendingAttendanceRequestsPortAdapter implements SectionPendingAttendanceRequestsPort {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;

    public SectionPendingAttendanceRequestsPortAdapter(AttendanceChangeRequestRepository attendanceChangeRequestRepository) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
    }

    @Override
    public Map<Long, Long> countPendingBySubjectSectionIds(Collection<Long> subjectSectionIds) {
        if (subjectSectionIds.isEmpty()) {
            return Map.of();
        }
        return toMap(attendanceChangeRequestRepository.countPendingBySubjectSectionIds(subjectSectionIds));
    }

    @Override
    public Map<Long, Long> countPendingByCourseSectionIds(Collection<Long> courseSectionIds) {
        if (courseSectionIds.isEmpty()) {
            return Map.of();
        }
        return toMap(attendanceChangeRequestRepository.countPendingByCourseSectionIds(courseSectionIds));
    }

    private Map<Long, Long> toMap(List<SectionRequestCount> counts) {
        return counts.stream().collect(Collectors.toMap(SectionRequestCount::sectionId, SectionRequestCount::requests, Long::sum));
    }
}
