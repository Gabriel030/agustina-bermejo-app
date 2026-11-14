package com.red_escuela.backend.demo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCatalogPrerequisitesTest {
    private static final int SUBJECTS_PER_TERM_HALF = 3;

    @Test
    void everyPrerequisiteOfTheFullCatalogEndsBeforeItsSubjectStarts() {
        List<String> violations = new ArrayList<>();
        for (DemoCatalog.ProgramSpec program : DemoDataset.of(DemoDataset.FULL).programs()) {
            Map<String, int[]> positions = new HashMap<>();
            for (int year = 0; year < program.subjectsByYear().size(); year++) {
                List<String> subjects = program.subjectsByYear().get(year);
                for (int position = 0; position < subjects.size(); position++) {
                    positions.put(subjects.get(position), new int[] {year + 1, position});
                }
            }
            positions.forEach((subject, target) -> {
                String prerequisite = DemoCatalog.prerequisiteOf(subject);
                int[] required = prerequisite == null ? null : positions.get(prerequisite);
                if (required != null && termOf(required) >= termOf(target)) {
                    violations.add(program.name() + ": " + prerequisite + " -> " + subject);
                }
            });
        }

        assertThat(violations).isEmpty();
    }

    private static int termOf(int[] position) {
        return (position[0] - 1) * 2 + (position[1] < SUBJECTS_PER_TERM_HALF ? 1 : 2);
    }
}
