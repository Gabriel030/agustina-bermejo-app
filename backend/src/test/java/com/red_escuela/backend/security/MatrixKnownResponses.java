package com.red_escuela.backend.security;

import java.util.Map;

final class MatrixKnownResponses {

    private static final String NO_PROFILE = "la persona de la matriz no tiene perfil de alumno o docente asociado";
    private static final String NO_DATA = "la persona de la matriz no tiene datos asociados a este recurso";

    private static final String REFERENCED_ENTITY = "el cuerpo minimo referencia entidades por id que existen o no segun los datos que dejaron otros tests";

    private static final Map<String, String> ABSENT_WITHOUT_PATH_VARIABLE = Map.ofEntries(
            Map.entry("GET /exam-boards", NO_PROFILE),
            Map.entry("GET /late-payment-settings/current", "no hay configuracion de mora cargada en el fixture"),
            Map.entry("GET /regular-certificates/eligibility", NO_PROFILE),
            Map.entry("GET /me/attendance-change-requests", NO_PROFILE),
            Map.entry("GET /me/avatar", "la persona de la matriz no subio avatar"),
            Map.entry("GET /me/bank-transfer-receipts", NO_PROFILE),
            Map.entry("GET /me/course-enrollment-applications", NO_PROFILE),
            Map.entry("GET /me/course-enrollment-preview", NO_PROFILE),
            Map.entry("GET /me/course-enrollments", NO_PROFILE),
            Map.entry("GET /me/course-section-enrollments", NO_PROFILE),
            Map.entry("GET /me/credit-balance", NO_PROFILE),
            Map.entry("GET /me/credit-notes", NO_PROFILE),
            Map.entry("GET /me/documents", NO_PROFILE),
            Map.entry("GET /me/enrollment-payment-status", NO_PROFILE),
            Map.entry("GET /me/exam-board-registrations", NO_PROFILE),
            Map.entry("GET /me/payments", NO_PROFILE),
            Map.entry("GET /me/regular-certificates", NO_PROFILE),
            Map.entry("GET /me/regular-certificates/eligibility", NO_PROFILE),
            Map.entry("POST /me/regular-certificates", NO_PROFILE),
            Map.entry("GET /me/program-enrollment-applications", NO_PROFILE),
            Map.entry("GET /me/program-enrollments", NO_PROFILE),
            Map.entry("GET /me/refund-eligibility", NO_PROFILE),
            Map.entry("GET /me/refund-requests", NO_PROFILE),
            Map.entry("GET /me/refunds", NO_PROFILE),
            Map.entry("GET /me/section-enrollments", NO_PROFILE),
            Map.entry("GET /me/section-evaluation-configs", NO_PROFILE),
            Map.entry("GET /me/student-discounts", NO_PROFILE),
            Map.entry("GET /me/teaching-sections", NO_PROFILE),
            Map.entry("POST /courses", REFERENCED_ENTITY),
            Map.entry("POST /prospects", REFERENCED_ENTITY),
            Map.entry("POST /prospects/by-staff", REFERENCED_ENTITY),
            Map.entry("POST /graduation-certificates", REFERENCED_ENTITY),
            Map.entry("POST /regular-certificates", REFERENCED_ENTITY),
            Map.entry("POST /director-approval-requests", REFERENCED_ENTITY),
            Map.entry("POST /me/bank-transfer-receipts", NO_DATA),
            Map.entry("POST /me/course-enrollments", NO_PROFILE),
            Map.entry("POST /me/program-enrollment-applications", NO_PROFILE),
            Map.entry("POST /me/program-enrollment-applications/with-documents", NO_PROFILE),
            Map.entry("POST /me/refund-requests", NO_PROFILE),
            Map.entry("POST /me/refund-requests/preview", NO_PROFILE),
            Map.entry("POST /me/section-enrollments", NO_PROFILE));

    private MatrixKnownResponses() {
    }

    static AccessExpectation.Tolerance toleranceFor(String key, AccessExpectation anonymous) {
        boolean absent = key.contains("{") || ABSENT_WITHOUT_PATH_VARIABLE.containsKey(key);
        boolean unauthorized = anonymous == AccessExpectation.PUBLIC;
        return new AccessExpectation.Tolerance(absent, unauthorized);
    }
}
