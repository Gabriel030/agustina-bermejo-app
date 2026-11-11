package com.red_escuela.backend.evaluation.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface StudentPaymentsStandingPort {

    Map<Long, PaymentsStanding> findPaymentsStanding(Collection<Long> studentIds, LocalDate today);
}
