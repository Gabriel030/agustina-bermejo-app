package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.service.OverdueContactSegment;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OverdueDebtAudience {

    private final CurrentBillingModePort billingMode;
    private final OverdueContactSegment overdueContactSegment;
    private final Clock clock;

    public OverdueDebtAudience(CurrentBillingModePort billingMode, OverdueContactSegment overdueContactSegment, Clock clock) {
        this.billingMode = billingMode;
        this.overdueContactSegment = overdueContactSegment;
        this.clock = clock;
    }

    public boolean isAvailable() {
        return billingMode.currentMode().isEnabled();
    }

    public Optional<Set<Long>> overdueStudentIds() {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return Optional.of(overdueContactSegment.findAllMembers(LocalDate.now(clock)).stream()
                .map(StudentSegmentMember::studentId).collect(Collectors.toSet()));
    }
}
