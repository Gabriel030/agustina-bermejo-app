package com.red_escuela.backend.demo;

import com.red_escuela.backend.demo.infrastructure.DemoHttpClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoSeedSession {

    private DemoHttpClient api;
    private final Map<String, Long> programIdsByName = new LinkedHashMap<>();
    private final Map<String, Long> courseIdsByName = new LinkedHashMap<>();
    private final Map<String, Long> studentIdsByUsername = new LinkedHashMap<>();
    private final Map<String, List<Long>> firstYearSectionIdsByProgram = new LinkedHashMap<>();

    public void open(int port) {
        this.api = new DemoHttpClient(port);
        programIdsByName.clear();
        courseIdsByName.clear();
        firstYearSectionIdsByProgram.clear();
        studentIdsByUsername.clear();
    }

    public DemoHttpClient api() {
        if (api == null) {
            throw new IllegalStateException("La sesión del seed demo no está abierta");
        }
        return api;
    }

    public Map<String, Long> programIdsByName() {
        return programIdsByName;
    }

    public Map<String, Long> courseIdsByName() {
        return courseIdsByName;
    }

    public Map<String, Long> studentIdsByUsername() {
        return studentIdsByUsername;
    }

    public Map<String, List<Long>> firstYearSectionIdsByProgram() {
        return firstYearSectionIdsByProgram;
    }

    public List<Long> enrolledSectionIdsOf(DemoDataset dataset) {
        return dataset.programs().stream().flatMap(program -> enrolledSectionIdsOf(program.name()).stream()).distinct().toList();
    }

    public List<Long> enrolledSectionIdsOf(String programName) {
        List<Long> sectionIds = firstYearSectionIdsByProgram.get(programName);
        return sectionIds.subList(0, Math.min(DemoEnrollmentSeedStep.SECTIONS_PER_STUDENT, sectionIds.size()));
    }

    public Long findIdByName(JsonNode items, String name) {
        for (JsonNode item : items) {
            if (name.equals(item.path("name").asString(""))) {
                return item.get("id").asLong();
            }
        }
        return null;
    }
}
