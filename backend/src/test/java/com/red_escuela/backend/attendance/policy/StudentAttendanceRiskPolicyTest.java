package com.red_escuela.backend.attendance.policy;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.identity.api.StudentAttendanceRisk;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceRiskPolicyTest {

    private static final Percentage MAX = Percentage.ofPoints(BigDecimal.valueOf(25));

    private AttendanceStanding standing(long held, long absences, Percentage max) {
        Percentage absencePercent = Percentage.ratio(absences, held).orElse(null);
        boolean exceeds = max != null && absencePercent != null && absencePercent.exceeds(max);
        return new AttendanceStanding(held, held - absences, absences, 0, absencePercent, null, max, exceeds, null, null);
    }

    @Test
    void exactlyEightyPercentOfAllowedAbsencesIsNearLimit() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(100, 20, MAX))).isEqualTo(StudentAttendanceRisk.NEAR_LIMIT);
    }

    @Test
    void justBelowEightyPercentOfAllowedAbsencesIsNone() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(100, 19, MAX))).isEqualTo(StudentAttendanceRisk.NONE);
    }

    @Test
    void exactlyTheLimitIsStillNearLimitNotFree() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(100, 25, MAX))).isEqualTo(StudentAttendanceRisk.NEAR_LIMIT);
    }

    @Test
    void aboveTheLimitIsFree() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(100, 26, MAX))).isEqualTo(StudentAttendanceRisk.FREE);
    }

    @Test
    void noClassesHeldIsNone() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(0, 0, MAX))).isEqualTo(StudentAttendanceRisk.NONE);
    }

    @Test
    void withoutAbsencesIsNoneEvenWithZeroLimit() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(10, 0, Percentage.ofPoints(BigDecimal.ZERO)))).isEqualTo(StudentAttendanceRisk.NONE);
    }

    @Test
    void missingLimitIsNone() {
        assertThat(StudentAttendanceRiskPolicy.classify(standing(10, 9, null))).isEqualTo(StudentAttendanceRisk.NONE);
    }

    @Test
    void worstKeepsTheMostSevereLevel() {
        assertThat(StudentAttendanceRiskPolicy.worst(StudentAttendanceRisk.NONE, StudentAttendanceRisk.FREE)).isEqualTo(StudentAttendanceRisk.FREE);
        assertThat(StudentAttendanceRiskPolicy.worst(StudentAttendanceRisk.FREE, StudentAttendanceRisk.NEAR_LIMIT)).isEqualTo(StudentAttendanceRisk.FREE);
    }
}
