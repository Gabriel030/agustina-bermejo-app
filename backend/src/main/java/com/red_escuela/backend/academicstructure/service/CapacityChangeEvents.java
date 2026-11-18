package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.events.CourseCapacityChangedEvent;
import com.red_escuela.backend.academicstructure.events.CourseSectionCapacityChangedEvent;
import com.red_escuela.backend.academicstructure.events.SectionCapacityChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class CapacityChangeEvents {

    private final ApplicationEventPublisher eventPublisher;

    public CapacityChangeEvents(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void programSectionChanged(Long sectionId, Integer previousCapacity, Integer newCapacity) {
        if (isIncrease(previousCapacity, newCapacity)) {
            eventPublisher.publishEvent(SectionCapacityChangedEvent.of(sectionId, previousCapacity, newCapacity));
        }
    }

    public void courseSectionChanged(Long sectionId, Integer previousCapacity, Integer newCapacity) {
        if (isIncrease(previousCapacity, newCapacity)) {
            eventPublisher.publishEvent(CourseSectionCapacityChangedEvent.of(sectionId, previousCapacity, newCapacity));
        }
    }

    public void courseChanged(Long courseId, Integer previousCapacity, Integer newCapacity) {
        if (isIncrease(previousCapacity, newCapacity)) {
            eventPublisher.publishEvent(CourseCapacityChangedEvent.of(courseId, previousCapacity, newCapacity));
        }
    }

    static boolean isIncrease(Integer previousCapacity, Integer newCapacity) {
        if (previousCapacity == null) {
            return false;
        }
        return newCapacity == null || newCapacity > previousCapacity;
    }
}
