package com.red_escuela.backend.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class MeEndpointsIsolationTest extends AuthorizationMatrixSupport {

    private static final long MISSING_ID = 999_999L;

    private static Pattern idOf(Long id) {
        return Pattern.compile("\"id\":" + id + "[,}]");
    }

    private static Pattern literal(String text) {
        return Pattern.compile(Pattern.quote(text));
    }

    enum IsolationCase {
        ANNOUNCEMENTS("GET /me/announcements", MatrixRole.ALUMNO, "/me/announcements",
                fixtures -> literal("Aviso matriz"), "[]"),
        COURSE_ENROLLMENT_APPLICATIONS("GET /me/course-enrollment-applications", MatrixRole.ALUMNO,
                "/me/course-enrollment-applications", fixtures -> idOf(fixtures.courseApplicationId()), "[]"),
        COURSE_SECTION_ENROLLMENTS("GET /me/course-section-enrollments", MatrixRole.ALUMNO,
                "/me/course-section-enrollments", fixtures -> idOf(fixtures.resourceIdFor(OwnershipKind.COURSE_SECTION_ENROLLMENT)), "[]"),
        COURSE_ENROLLMENTS("GET /me/course-enrollments", MatrixRole.ALUMNO,
                "/me/course-enrollments", fixtures -> idOf(fixtures.resourceIdFor(OwnershipKind.COURSE_ENROLLMENT)), "[]"),
        PROGRAM_ENROLLMENTS("GET /me/program-enrollments", MatrixRole.ALUMNO,
                "/me/program-enrollments", fixtures -> idOf(fixtures.resourceIdFor(OwnershipKind.PROGRAM_ENROLLMENT)), "[]"),
        SECTION_ENROLLMENTS("GET /me/section-enrollments", MatrixRole.ALUMNO,
                "/me/section-enrollments", fixtures -> idOf(fixtures.resourceIdFor(OwnershipKind.SECTION_ENROLLMENT)), "[]"),
        EXAM_BOARD_REGISTRATIONS("GET /me/exam-board-registrations", MatrixRole.ALUMNO,
                "/me/exam-board-registrations", fixtures -> idOf(fixtures.resourceIdFor(OwnershipKind.EXAM_BOARD_REGISTRATION)), "[]"),
        ENROLLMENT_PAYMENT_STATUS("GET /me/enrollment-payment-status", MatrixRole.ALUMNO,
                "/me/enrollment-payment-status",
                fixtures -> Pattern.compile("\"enrollmentId\":" + fixtures.resourceIdFor(OwnershipKind.PROGRAM_ENROLLMENT) + "[,}]"), "[]"),
        NOTIFICATIONS("GET /me/notifications", MatrixRole.ALUMNO, "/me/notifications",
                fixtures -> literal("Notificacion matriz"), "\"items\":[]"),
        UNREAD_COUNT("GET /me/notifications/unread-count", MatrixRole.ALUMNO, "/me/notifications/unread-count",
                fixtures -> literal("\"count\":1"), "\"count\":0"),
        SESSIONS("GET /me/sessions", MatrixRole.ALUMNO, "/me/sessions",
                fixtures -> idOf(fixtures.resourceIdFor(OwnershipKind.SESSION)), "[]"),
        TEACHING_SECTIONS("GET /me/teaching-sections", MatrixRole.DOCENTE, "/me/teaching-sections",
                fixtures -> literal("Comision matriz"), "[]"),
        SECTION_EVALUATION_CONFIGS("GET /me/section-evaluation-configs", MatrixRole.DOCENTE, "/me/section-evaluation-configs",
                fixtures -> literal("Comision matriz"), "[]"),
        COURSE_SECTION_EVALUATION_CONFIGS("GET /me/course-section-evaluation-configs", MatrixRole.DOCENTE,
                "/me/course-section-evaluation-configs", fixtures -> literal("Comision curso matriz"), "[]");

        private final String route;
        private final MatrixRole viewer;
        private final String uri;
        private final Function<OwnershipFixtures, Pattern> ownerMarker;
        private final String strangerBody;

        IsolationCase(String route, MatrixRole viewer, String uri, Function<OwnershipFixtures, Pattern> ownerMarker, String strangerBody) {
            this.route = route;
            this.viewer = viewer;
            this.uri = uri;
            this.ownerMarker = ownerMarker;
            this.strangerBody = strangerBody;
        }
    }

    private static final Set<String> COVERED_BY_OTHER_ISOLATION_TESTS = Set.of(
            "GET /me/annual-upfront-requests", "GET /me/bank-transfer-receipts", "GET /me/calendar",
            "GET /me/calendar/export.ics", "GET /me/calendar/feed", "DELETE /me/calendar/feed/token",
            "POST /me/calendar/feed/token", "GET /me/certificates", "PUT /me/contact-consents", "GET /me/payments",
            "GET /me/procedures/approved", "GET /me/refunds", "GET /me/regular-certificates",
            "POST /me/regular-certificates", "GET /me/regular-certificates/eligibility",
            "GET /me/student-discounts", "GET /me/waitlist-positions");

    private static final Set<String> COVERED_BY_SPECIFIC_CASES_HERE = Set.of(
            "GET /me/exam-boards", "GET /me/notification-preferences", "PUT /me/notification-preferences",
            "DELETE /me/sessions");

    private static final Set<String> FUNCTIONAL_ONLY_WITHOUT_SECOND_USER = Set.of(
            "GET /me", "PUT /me", "POST /me/approved-subjects-certificates", "GET /me/attendance-change-requests",
            "GET /me/avatar", "POST /me/avatar", "POST /me/bank-transfer-receipts", "GET /me/contact-consents",
            "GET /me/course-enrollment-preview", "POST /me/course-enrollments", "GET /me/credit-balance",
            "GET /me/credit-notes", "GET /me/documents", "POST /me/exam-board-registrations",
            "POST /me/legal-acceptances", "POST /me/legal-acceptances/guardian-confirmation/resend",
            "GET /me/legal-acceptances/pending", "GET /me/modules", "GET /me/newsletter-preference",
            "PUT /me/newsletter-preference", "PUT /me/password", "GET /me/payment-methods",
            "GET /me/program-enrollment-applications", "POST /me/program-enrollment-applications",
            "POST /me/program-enrollment-applications/with-documents", "GET /me/refund-eligibility",
            "GET /me/refund-requests", "POST /me/refund-requests", "POST /me/refund-requests/preview",
            "GET /me/schedule", "POST /me/section-enrollments");

    private MockHttpServletResponse call(String uri, String token) throws Exception {
        return mockMvc.perform(get(uri).header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private String tokenOfOwner(MatrixRole viewer) {
        return viewer == MatrixRole.DOCENTE
                ? ownership.ownerTokenFor(OwnershipKind.SECTION_INSTRUCTOR)
                : ownership.ownerTokenFor(OwnershipKind.NOTIFICATION);
    }

    private String tokenOfStranger(MatrixRole viewer) {
        return viewer == MatrixRole.DOCENTE
                ? ownership.strangerTokenFor(OwnershipKind.SECTION_INSTRUCTOR)
                : ownership.strangerTokenFor(OwnershipKind.NOTIFICATION);
    }

    private static String withoutVolatileFields(String body) {
        return body.replaceAll("\"requestId\":\"[^\"]*\"", "").replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    @ParameterizedTest
    @EnumSource(IsolationCase.class)
    void theOwnerSeesItsDataThroughTheRoute(IsolationCase isolationCase) throws Exception {
        MockHttpServletResponse response = call(isolationCase.uri, tokenOfOwner(isolationCase.viewer));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).containsPattern(isolationCase.ownerMarker.apply(ownership));
    }

    @ParameterizedTest
    @EnumSource(IsolationCase.class)
    void anotherPersonNeverSeesTheDataOfTheOwnerThroughTheRoute(IsolationCase isolationCase) throws Exception {
        MockHttpServletResponse response = call(isolationCase.uri, tokenOfStranger(isolationCase.viewer));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContainPattern(isolationCase.ownerMarker.apply(ownership));
        assertThat(response.getContentAsString()).contains(isolationCase.strangerBody);
    }

    @Test
    void availableExamBoardsDoNotDependOnWhoAsks() throws Exception {
        String uri = "/me/exam-boards?subjectId=" + ownership.subjectId();

        MockHttpServletResponse owner = call(uri, ownership.ownerTokenFor(OwnershipKind.NOTIFICATION));
        MockHttpServletResponse stranger = call(uri, ownership.strangerTokenFor(OwnershipKind.NOTIFICATION));

        assertThat(owner.getStatus()).isEqualTo(200);
        assertThat(stranger.getStatus()).isEqualTo(200);
        assertThat(stranger.getContentAsString()).isEqualTo(owner.getContentAsString());
        assertThat(stranger.getContentAsString()).doesNotContain("studentId");
    }

    @Test
    void notificationPreferencesOfOnePersonNeverChangeOrLeakToAnother() throws Exception {
        String ownerToken = ownership.ownerTokenFor(OwnershipKind.NOTIFICATION);
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.NOTIFICATION);
        String preferences = call("/me/notification-preferences", ownerToken).getContentAsString();
        Map<String, Map<String, Boolean>> byEvent = JsonPath.read(preferences, "$.preferences");
        Map<String, List<String>> mandatory = JsonPath.read(preferences, "$.mandatoryChannels");
        String event = null;
        String channel = null;
        for (Map.Entry<String, Map<String, Boolean>> entry : byEvent.entrySet()) {
            for (Map.Entry<String, Boolean> channelEntry : entry.getValue().entrySet()) {
                boolean locked = mandatory.getOrDefault(entry.getKey(), List.of()).contains(channelEntry.getKey());
                if (!locked && channelEntry.getValue() && event == null) {
                    event = entry.getKey();
                    channel = channelEntry.getKey();
                }
            }
        }
        assertThat(event).isNotNull();

        int updateStatus = mockMvc.perform(put("/me/notification-preferences")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferences\":{\"" + event + "\":{\"" + channel + "\":false}}}"))
                .andReturn().getResponse().getStatus();

        assertThat(updateStatus).isEqualTo(200);
        Boolean ownerValue = JsonPath.read(call("/me/notification-preferences", ownerToken).getContentAsString(),
                "$.preferences." + event + "." + channel);
        Boolean strangerValue = JsonPath.read(call("/me/notification-preferences", strangerToken).getContentAsString(),
                "$.preferences." + event + "." + channel);
        assertThat(ownerValue).isFalse();
        assertThat(strangerValue).isTrue();
    }

    @Test
    void updatingNotificationPreferencesAsAnotherPersonLeavesTheOwnerUntouched() throws Exception {
        String ownerToken = ownership.ownerTokenFor(OwnershipKind.NOTIFICATION);
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.NOTIFICATION);
        String before = call("/me/notification-preferences", ownerToken).getContentAsString();
        Map<String, Map<String, Boolean>> byEvent = JsonPath.read(before, "$.preferences");
        Map<String, List<String>> mandatory = JsonPath.read(before, "$.mandatoryChannels");
        StringBuilder changes = new StringBuilder();
        for (Map.Entry<String, Map<String, Boolean>> entry : byEvent.entrySet()) {
            for (Map.Entry<String, Boolean> channelEntry : entry.getValue().entrySet()) {
                boolean locked = mandatory.getOrDefault(entry.getKey(), List.of()).contains(channelEntry.getKey());
                if (!locked && channelEntry.getValue()) {
                    if (!changes.isEmpty()) {
                        changes.append(',');
                    }
                    changes.append("\"").append(entry.getKey()).append("\":{\"").append(channelEntry.getKey()).append("\":false}");
                }
            }
        }
        assertThat(changes).isNotEmpty();

        int status = mockMvc.perform(put("/me/notification-preferences")
                .header("Authorization", "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferences\":{" + changes + "}}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        assertThat(call("/me/notification-preferences", ownerToken).getContentAsString()).isEqualTo(before);
    }

    @Test
    void revokingAllSessionsAsAnotherPersonKeepsTheSessionsOfTheOwner() throws Exception {
        String ownerToken = ownership.ownerTokenFor(OwnershipKind.SESSION);
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.SESSION);
        Long ownerSessionId = ownership.resourceIdFor(OwnershipKind.SESSION);

        int status = mockMvc.perform(delete("/me/sessions").header("Authorization", "Bearer " + strangerToken))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        assertThat(call("/me/sessions", ownerToken).getContentAsString()).containsPattern(idOf(ownerSessionId));
    }

    @Test
    void recoveryOfAForeignGradeItemLooksIdenticalToAMissingOne() throws Exception {
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.GRADE_ITEM_INSTRUCTOR);
        Long gradeItemId = ownership.resourceIdFor(OwnershipKind.GRADE_ITEM_INSTRUCTOR);

        MockHttpServletResponse foreign = postJson("/grade-items/" + gradeItemId + "/recovery", "{\"score\":7}", strangerToken);
        MockHttpServletResponse missing = postJson("/grade-items/" + MISSING_ID + "/recovery", "{\"score\":7}", strangerToken);

        assertForeignLooksMissing(foreign, missing);
    }

    @Test
    void sectionEvaluationConfigOfAForeignSectionLooksIdenticalToAMissingOne() throws Exception {
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.SECTION_INSTRUCTOR);
        Long sectionId = ownership.resourceIdFor(OwnershipKind.SECTION_INSTRUCTOR);

        assertForeignLooksMissing(
                call("/me/section-evaluation-configs/" + sectionId, strangerToken),
                call("/me/section-evaluation-configs/" + MISSING_ID, strangerToken));
        assertForeignLooksMissing(
                postJson("/me/section-evaluation-configs/" + sectionId + "/submit", null, strangerToken),
                postJson("/me/section-evaluation-configs/" + MISSING_ID + "/submit", null, strangerToken));
    }

    @Test
    void withdrawingAForeignExamBoardRegistrationLooksIdenticalToAMissingOne() throws Exception {
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.EXAM_BOARD_REGISTRATION);
        Long registrationId = ownership.resourceIdFor(OwnershipKind.EXAM_BOARD_REGISTRATION);

        MockHttpServletResponse foreign = postJson("/me/exam-board-registrations/" + registrationId + "/withdraw", null, strangerToken);
        MockHttpServletResponse missing = postJson("/me/exam-board-registrations/" + MISSING_ID + "/withdraw", null, strangerToken);

        assertForeignLooksMissing(foreign, missing);
    }

    @Test
    void changingTheSectionOfAForeignEnrollmentLooksIdenticalToAMissingOne() throws Exception {
        String strangerToken = ownership.strangerTokenFor(OwnershipKind.SECTION_ENROLLMENT);
        Long enrollmentId = ownership.resourceIdFor(OwnershipKind.SECTION_ENROLLMENT);
        String body = "{\"newSectionId\":" + ownership.resourceIdFor(OwnershipKind.SECTION_INSTRUCTOR) + "}";

        MockHttpServletResponse foreign = postJson("/me/section-enrollments/" + enrollmentId + "/change-section", body, strangerToken);
        MockHttpServletResponse missing = postJson("/me/section-enrollments/" + MISSING_ID + "/change-section", body, strangerToken);

        assertForeignLooksMissing(foreign, missing);
    }

    @Test
    void everyMeRouteWithoutIdInTheMatrixIsClassifiedByAnIsolationTest() {
        Set<String> classified = new LinkedHashSet<>();
        for (IsolationCase isolationCase : IsolationCase.values()) {
            classified.add(isolationCase.route);
        }
        classified.addAll(COVERED_BY_OTHER_ISOLATION_TESTS);
        classified.addAll(COVERED_BY_SPECIFIC_CASES_HERE);
        classified.addAll(FUNCTIONAL_ONLY_WITHOUT_SECOND_USER);

        Set<String> routes = new LinkedHashSet<>();
        for (AuthorizationMatrixRow row : AuthorizationMatrixFile.read()) {
            if ((row.path().equals("/me") || row.path().startsWith("/me/")) && !row.path().contains("{")) {
                routes.add(row.key());
            }
        }

        assertThat(routes).isSubsetOf(classified);
        assertThat(classified).isSubsetOf(routes);
    }

    private MockHttpServletResponse postJson(String uri, String body, String token) throws Exception {
        var request = post(uri).header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private void assertForeignLooksMissing(MockHttpServletResponse foreign, MockHttpServletResponse missing) throws Exception {
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(withoutVolatileFields(foreign.getContentAsString())).isEqualTo(withoutVolatileFields(missing.getContentAsString()));
        assertThat(foreign.getContentType()).isEqualTo(missing.getContentType());
    }
}
