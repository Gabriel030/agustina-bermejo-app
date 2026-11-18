package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.events.CourseCapacityChangedEvent;
import com.red_escuela.backend.academicstructure.events.CourseSectionCapacityChangedEvent;
import com.red_escuela.backend.academicstructure.events.SectionCapacityChangedEvent;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WaitlistCapacityChangeListener {

    private final WaitlistPromoter waitlistPromoter;

    public WaitlistCapacityChangeListener(WaitlistPromoter waitlistPromoter) {
        this.waitlistPromoter = waitlistPromoter;
    }

    @EventListener
    public void promoteAfterProgramSectionCapacityIncrease(SectionCapacityChangedEvent event) {
        waitlistPromoter.promoteWhileSeatsAreFree(WaitlistKind.PROGRAM_SECTION, event.sectionId());
    }

    @EventListener
    public void promoteAfterCourseSectionCapacityIncrease(CourseSectionCapacityChangedEvent event) {
        waitlistPromoter.promoteWhileSeatsAreFree(WaitlistKind.COURSE_SECTION, event.sectionId());
    }

    @EventListener
    public void promoteAfterCourseCapacityIncrease(CourseCapacityChangedEvent event) {
        waitlistPromoter.promoteWhileSeatsAreFree(WaitlistKind.COURSE, event.courseId());
    }
}
