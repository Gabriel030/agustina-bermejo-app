package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Optional;

public interface ReminderPaymentGoalQuery {

    Optional<Percentage> findPercentageOfRemindersFollowedByPaymentAtLeast();
}
