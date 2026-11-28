package com.red_escuela.backend.academicstructure.domain;

public enum CourseReadinessItemCode {
    COURSE_RESPONSIBLE(false),
    COURSE_SECTIONS(false),
    SECTION_PRECEPTORS(true);

    private final boolean blocking;

    CourseReadinessItemCode(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean blocking() {
        return blocking;
    }
}
