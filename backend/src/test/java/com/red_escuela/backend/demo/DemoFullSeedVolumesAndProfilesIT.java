package com.red_escuela.backend.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoFullSeedVolumesAndProfilesIT extends AbstractFullDemoSeedIT {
    @Autowired
    private DemoSeedSession session;

    @Test
    void fullSeedCreatesTheDatasetVolumesOfStudentsAndTeachers() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.FULL);

        for (int index = 0; index < dataset.studentCount(); index++) {
            String username = DemoPeopleSeedStep.studentUsername(index);
            assertThat(jdbcTemplate.queryForObject("select count(*) from person where username = ?", Integer.class, username)).isEqualTo(1);
            assertThat(api.get(username, "/me/program-enrollments")).hasSize(1);
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from student", Integer.class)).isGreaterThanOrEqualTo(dataset.studentCount());
        assertThat(jdbcTemplate.queryForObject("select count(*) from instructor", Integer.class)).isGreaterThanOrEqualTo(dataset.teacherCount());
    }

    @Test
    void fullSeedGivesEveryEnrollmentOneOfTheAcademicProfiles() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.FULL);
        Set<DemoAcademicProfile> profilesSeen = EnumSet.noneOf(DemoAcademicProfile.class);
        int rosterTotal = 0;

        for (Long sectionId : session.enrolledSectionIdsOf(dataset)) {
            JsonNode roster = api.get(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/roster");
            assertThat(roster).isNotEmpty();
            rosterTotal += roster.size();
            for (JsonNode entry : roster) {
                long enrollmentId = entry.get("enrollmentId").asLong();
                List<BigDecimal> scores = examScoresOf(enrollmentId);
                int absences = absencesOf(enrollmentId);
                DemoAcademicProfile profile = Arrays.stream(DemoAcademicProfile.values())
                        .filter(candidate -> candidate.absences() == absences && sameScores(candidate.examScores(), scores))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Enrollment " + enrollmentId + " matches no academic profile: scores=" + scores + " absences=" + absences));
                profilesSeen.add(profile);
                assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from attendance where program_section_enrollment_id = ?", Integer.class, enrollmentId))
                        .isEqualTo(DemoAttendanceSeedStep.CLASSES_PER_SECTION);
            }
        }
        assertThat(profilesSeen).isNotEmpty();
        assertThat(rosterTotal).isPositive();
    }

    private List<BigDecimal> examScoresOf(long enrollmentId) {
        List<JsonNode> items = new ArrayList<>();
        api.get(DemoPersona.ADMIN, "/section-enrollments/" + enrollmentId + "/grade-items").forEach(items::add);
        return items.stream()
                .sorted(Comparator.comparingInt(item -> item.path("sequenceNumber").asInt(0)))
                .map(item -> item.get("score").decimalValue())
                .toList();
    }

    private int absencesOf(long enrollmentId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from attendance where program_section_enrollment_id = ? and status = 'ABSENT'", Integer.class, enrollmentId);
    }

    private boolean sameScores(List<BigDecimal> expected, List<BigDecimal> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index).compareTo(actual.get(index)) != 0) {
                return false;
            }
        }
        return true;
    }
}
