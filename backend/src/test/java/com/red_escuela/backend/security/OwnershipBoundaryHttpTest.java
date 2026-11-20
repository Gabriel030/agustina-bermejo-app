package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

class OwnershipBoundaryHttpTest extends AuthorizationMatrixSupport {

    private static final long MISSING_ID = 999_999L;

    enum Resource {
        CLOSURE_SHEET("/program-sections/%d/closure-sheet"),
        CLOSURE_EXPORT("/commission-closures/%d/export?format=xlsx"),
        EXAM_BOARD("/exam-boards/%d"),
        EXAM_BOARD_REGISTRATIONS("/exam-boards/%d/registrations");

        private final String template;

        Resource(String template) {
            this.template = template;
        }

        String uri(long id) {
            return template.formatted(id);
        }
    }

    enum StudentResource {
        INVOICE_MP_PREFERENCE(HttpMethod.POST, "/me/invoices/%d/mp-preference", OwnershipKind.INVOICE),
        INVOICE_PRICE_BREAKDOWN(HttpMethod.GET, "/me/invoices/%d/price-breakdown", OwnershipKind.INVOICE),
        NOTIFICATION_READ(HttpMethod.POST, "/me/notifications/%d/read", OwnershipKind.NOTIFICATION),
        SESSION_REVOKE(HttpMethod.DELETE, "/me/sessions/%d", OwnershipKind.SESSION);

        private final HttpMethod method;
        private final String template;
        private final OwnershipKind kind;

        StudentResource(HttpMethod method, String template, OwnershipKind kind) {
            this.method = method;
            this.template = template;
            this.kind = kind;
        }
    }

    static Stream<StudentResource> studentResources() {
        return Stream.of(StudentResource.values());
    }

    static Stream<Resource> resources() {
        return Stream.of(Resource.values());
    }

    private long existingIdOf(Resource resource) {
        return switch (resource) {
            case CLOSURE_SHEET -> ownership.closureSectionId();
            case CLOSURE_EXPORT -> ownership.closureId();
            case EXAM_BOARD, EXAM_BOARD_REGISTRATIONS -> ownership.examBoardId();
        };
    }

    private MockHttpServletResponse call(Resource resource, long id, String token) throws Exception {
        return mockMvc.perform(get(resource.uri(id)).header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private String strangerTeacherToken() {
        return ownership.strangerTokenFor(OwnershipKind.SECTION_INSTRUCTOR);
    }

    private void assertNotFoundWithoutCodeOrBody(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).doesNotContain("code");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("studentResources")
    void foreignResourceLooksIdenticalToMissingOneForStudent(StudentResource resource) throws Exception {
        String strangerToken = ownership.strangerTokenFor(resource.kind);
        long existingId = ownership.resourceIdFor(resource.kind);
        MockHttpServletResponse foreign = mockMvc.perform(request(resource.method, resource.template.formatted(existingId))
                .header("Authorization", "Bearer " + strangerToken)).andReturn().getResponse();
        MockHttpServletResponse missing = mockMvc.perform(request(resource.method, resource.template.formatted(MISSING_ID))
                .header("Authorization", "Bearer " + strangerToken)).andReturn().getResponse();

        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(foreign.getContentAsString()).isEqualTo(missing.getContentAsString());
        assertThat(foreign.getContentType()).isEqualTo(missing.getContentType());
    }

    @ParameterizedTest
    @MethodSource("resources")
    void foreignResourceLooksIdenticalToMissingOneForTeacher(Resource resource) throws Exception {
        MockHttpServletResponse foreign = call(resource, existingIdOf(resource), strangerTeacherToken());
        MockHttpServletResponse missing = call(resource, MISSING_ID, strangerTeacherToken());

        assertNotFoundWithoutCodeOrBody(foreign);
        assertNotFoundWithoutCodeOrBody(missing);
        assertThat(foreign.getContentType()).isEqualTo(missing.getContentType());
    }

    @ParameterizedTest
    @MethodSource("resources")
    void teacherWithStudentRoleWithoutMembershipGetsTheSameNotFound(Resource resource) throws Exception {
        MockHttpServletResponse foreign = call(resource, existingIdOf(resource), ownership.teacherStudentToken());
        MockHttpServletResponse missing = call(resource, MISSING_ID, ownership.teacherStudentToken());

        assertNotFoundWithoutCodeOrBody(foreign);
        assertNotFoundWithoutCodeOrBody(missing);
    }

    @ParameterizedTest
    @MethodSource("resources")
    void teacherWithDirectorRoleKeepsStaffViewOfForeignResource(Resource resource) throws Exception {
        MockHttpServletResponse response = call(resource, existingIdOf(resource), ownership.teacherDirectorToken());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @MethodSource("resources")
    void owningTeacherStillReadsOwnResource(Resource resource) throws Exception {
        MockHttpServletResponse response = call(resource, existingIdOf(resource),
                ownership.ownerTokenFor(OwnershipKind.SECTION_INSTRUCTOR));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void multiRoleTeacherWithoutMembershipIsDeniedWhileTeacherDirectorIsNot() throws Exception {
        MockHttpServletResponse withStudentRole = call(Resource.CLOSURE_EXPORT, ownership.closureId(), ownership.teacherStudentToken());
        MockHttpServletResponse withDirectorRole = call(Resource.CLOSURE_EXPORT, ownership.closureId(), ownership.teacherDirectorToken());

        assertThat(withStudentRole.getStatus()).isEqualTo(404);
        assertThat(withDirectorRole.getStatus()).isEqualTo(200);
    }
}
