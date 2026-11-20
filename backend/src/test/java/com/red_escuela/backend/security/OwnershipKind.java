package com.red_escuela.backend.security;

import java.util.Arrays;

enum OwnershipKind {
    PROGRAM_ENROLLMENT("program-enrollment", MatrixRole.ALUMNO, 404),
    SECTION_ENROLLMENT("section-enrollment", MatrixRole.ALUMNO, 404),
    COURSE_ENROLLMENT("course-enrollment", MatrixRole.ALUMNO, 404),
    COURSE_SECTION_ENROLLMENT("course-section-enrollment", MatrixRole.ALUMNO, 404),
    SECTION_INSTRUCTOR("section-instructor", MatrixRole.DOCENTE, 404),
    ATTENDANCE_SECTION_INSTRUCTOR("attendance-section-instructor", MatrixRole.DOCENTE, 404),
    ANNUAL_UPFRONT_REQUEST("annual-upfront-request", MatrixRole.ALUMNO, 404),
    STAFF_RECORD("staff-record", MatrixRole.DOCENTE, 404),
    EXAM_BOARD_TRIBUNAL("exam-board-tribunal", MatrixRole.DOCENTE, 404),
    INVOICE("invoice", MatrixRole.ALUMNO, 404),
    NOTIFICATION("notification", MatrixRole.ALUMNO, 404),
    SESSION("session", MatrixRole.ALUMNO, 404),
    SECTION_ENROLLMENT_INSTRUCTOR("section-enrollment-instructor", MatrixRole.DOCENTE, 404),
    COURSE_SECTION_ENROLLMENT_INSTRUCTOR("course-section-enrollment-instructor", MatrixRole.DOCENTE, 404),
    COURSE_SECTION_INSTRUCTOR("course-section-instructor", MatrixRole.DOCENTE, 404),
    AUTHORIZATION_REQUEST("authorization-request", MatrixRole.ADMINISTRATIVO, 404);

    private final String label;
    private final MatrixRole ownerRole;
    private final int strangerStatus;

    OwnershipKind(String label, MatrixRole ownerRole, int strangerStatus) {
        this.label = label;
        this.ownerRole = ownerRole;
        this.strangerStatus = strangerStatus;
    }

    String label() {
        return label;
    }

    MatrixRole ownerRole() {
        return ownerRole;
    }

    int strangerStatus() {
        return strangerStatus;
    }

    static OwnershipKind fromLabel(String label) {
        return Arrays.stream(values())
                .filter(kind -> kind.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ownership desconocido en la matriz: " + label));
    }
}
