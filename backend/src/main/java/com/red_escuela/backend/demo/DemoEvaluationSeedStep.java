package com.red_escuela.backend.demo;

import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoEvaluationSeedStep implements DemoSeedStep {
    public static final int STEP_ORDER = 40;

    private static final int FIRST_EXAM_DAYS_AGO = 56;
    private static final int SECOND_EXAM_DAYS_AGO = 21;

    private final DemoSeedSession session;

    public DemoEvaluationSeedStep(DemoSeedSession session) {
        this.session = session;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public String label() {
        return "notas";
    }

    @Override
    public void seed(DemoDataset dataset) {
        LocalDate today = ApplicationTime.today();
        for (DemoCatalog.ProgramSpec program : dataset.programs()) {
            List<Long> sectionIds = session.enrolledSectionIdsOf(program.name());
            for (int sectionPosition = 0; sectionPosition < sectionIds.size(); sectionPosition++) {
                gradeSection(sectionIds.get(sectionPosition), sectionPosition, today);
            }
        }
    }

    private void gradeSection(long sectionId, int sectionPosition, LocalDate today) {
        JsonNode roster = session.api().get(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/roster");
        for (int rosterPosition = 0; rosterPosition < roster.size(); rosterPosition++) {
            long sectionEnrollmentId = roster.get(rosterPosition).get("enrollmentId").asLong();
            List<BigDecimal> scores = DemoAcademicProfile.of(rosterPosition, sectionPosition).examScores();
            for (int exam = 0; exam < scores.size(); exam++) {
                int daysAgo = exam == 0 ? FIRST_EXAM_DAYS_AGO : SECOND_EXAM_DAYS_AGO;
                loadExam(sectionEnrollmentId, exam + 1, today.minusDays(daysAgo), scores.get(exam));
            }
        }
    }

    private void loadExam(long sectionEnrollmentId, int sequenceNumber, LocalDate date, BigDecimal score) {
        Map<String, Object> gradeItem = new LinkedHashMap<>();
        gradeItem.put("type", "EXAM");
        gradeItem.put("sequenceNumber", sequenceNumber);
        gradeItem.put("date", date.atStartOfDay().toString());
        gradeItem.put("score", score);
        session.api().post(DemoPersona.TEACHER, "/section-enrollments/" + sectionEnrollmentId + "/grade-items", gradeItem);
    }
}
