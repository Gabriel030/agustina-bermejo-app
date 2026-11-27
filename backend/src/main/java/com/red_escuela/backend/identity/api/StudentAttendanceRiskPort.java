package com.red_escuela.backend.identity.api;

import java.util.Collection;
import java.util.Map;

public interface StudentAttendanceRiskPort {
    Map<Long, StudentAttendanceRisk> riskByStudent(Collection<Long> studentIds);
}
