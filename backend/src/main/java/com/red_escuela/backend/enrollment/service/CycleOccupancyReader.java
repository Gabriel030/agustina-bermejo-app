package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CycleSectionRef;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse;
import com.red_escuela.backend.enrollment.dto.CycleOccupancyResponse.SectionOccupancy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CycleOccupancyReader {

    private final SectionFinder sectionFinder;
    private final SectionOccupancyCounter occupancyCounter;

    public CycleOccupancyReader(SectionFinder sectionFinder, SectionOccupancyCounter occupancyCounter) {
        this.sectionFinder = sectionFinder;
        this.occupancyCounter = occupancyCounter;
    }

    public CycleOccupancyResponse readOccupancy(Long cycleId) {
        var sections = sectionFinder.listSectionsOfCycle(cycleId).stream().map(this::occupancyOf).toList();
        return new CycleOccupancyResponse(cycleId, sections);
    }

    private SectionOccupancy occupancyOf(CycleSectionRef section) {
        long enrolled = occupancyCounter.countSeatsTaken(section.kind(), section.id());
        long waitlisted = occupancyCounter.countWaitlisted(section.kind(), section.id());
        Long available = section.capacity() == null ? null : Math.max(0L, section.capacity() - enrolled);
        return new SectionOccupancy(section.id(), section.kind(), section.name(), section.capacity(), enrolled,
                available, waitlisted);
    }
}
