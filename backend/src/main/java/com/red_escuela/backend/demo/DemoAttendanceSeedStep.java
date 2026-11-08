package com.red_escuela.backend.demo;

import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoAttendanceSeedStep implements DemoSeedStep {
    public static final int STEP_ORDER = 40;
    public static final int CLASSES_PER_SECTION = 4;

    private final DemoSeedSession session;

    public DemoAttendanceSeedStep(DemoSeedSession session) {
        this.session = session;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public String label() {
        return "asistencia";
    }

    @Override
    public void seed(DemoDataset dataset) {
        List<LocalDate> classDates = recentWeekdaysBefore(ApplicationTime.today(), CLASSES_PER_SECTION);
        List<Long> sectionIds = session.enrolledSectionIdsOf(dataset);
        for (int sectionPosition = 0; sectionPosition < sectionIds.size(); sectionPosition++) {
            recordSection(sectionIds.get(sectionPosition), sectionPosition, classDates);
        }
    }

    static List<LocalDate> recentWeekdaysBefore(LocalDate today, int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate day = today.minusDays(1);
        while (dates.size() < count) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(0, day);
            }
            day = day.minusDays(1);
        }
        return dates;
    }

    private void recordSection(long sectionId, int sectionPosition, List<LocalDate> classDates) {
        JsonNode roster = session.api().get(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/roster");
        if (roster.isEmpty()) {
            throw new IllegalStateException("La comisión " + sectionId + " no tiene alumnos para tomar asistencia");
        }
        for (int dateIndex = 0; dateIndex < classDates.size(); dateIndex++) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (int rosterPosition = 0; rosterPosition < roster.size(); rosterPosition++) {
                int absences = DemoAcademicProfile.of(rosterPosition, sectionPosition).absences();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("enrollmentId", roster.get(rosterPosition).get("enrollmentId").asLong());
                entry.put("status", dateIndex < absences ? "ABSENT" : "PRESENT");
                entries.add(entry);
            }
            session.api().post(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/attendance",
                    Map.of("classDate", classDates.get(dateIndex).toString(), "entries", entries));
        }
    }
}
