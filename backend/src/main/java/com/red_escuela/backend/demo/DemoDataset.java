package com.red_escuela.backend.demo;

import java.util.List;

public record DemoDataset(String name, int programCount, int courseCount, int studentCount, int teacherCount) {

    public static final String CI = "ci";
    public static final String FULL = "full";

    public static DemoDataset of(String name) {
        if (FULL.equals(name)) {
            return new DemoDataset(FULL, DemoCatalog.PROGRAMS.size(), DemoCatalog.COURSES.size(), 38, 8);
        }
        if (CI.equals(name)) {
            return new DemoDataset(CI, 2, 1, 12, 2);
        }
        throw new IllegalArgumentException("demo.dataset debe ser ci o full y llegó " + name);
    }

    public List<DemoCatalog.ProgramSpec> programs() {
        return DemoCatalog.PROGRAMS.subList(0, programCount);
    }

    public List<DemoCatalog.CourseSpec> courses() {
        return DemoCatalog.COURSES.subList(0, courseCount);
    }
}
