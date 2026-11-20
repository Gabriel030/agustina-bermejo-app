package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SectionWaitlistPromoter {

    private final WaitlistPromoter waitlistPromoter;

    public SectionWaitlistPromoter(WaitlistPromoter waitlistPromoter) {
        this.waitlistPromoter = waitlistPromoter;
    }

    @Transactional
    public void promoteNextFromWaitlist(Long sectionId) {
        waitlistPromoter.promoteNext(WaitlistKind.PROGRAM_SECTION, sectionId);
    }
}
