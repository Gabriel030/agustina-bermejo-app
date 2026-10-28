package com.red_escuela.backend.security;

import java.util.Arrays;

enum OwnershipKind {
    PROGRAM_ENROLLMENT("program-enrollment", MatrixRole.ALUMNO, 404),
    SECTION_ENROLLMENT("section-enrollment", MatrixRole.ALUMNO, 404),
    SECTION_INSTRUCTOR("section-instructor", MatrixRole.DOCENTE, 403);

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
