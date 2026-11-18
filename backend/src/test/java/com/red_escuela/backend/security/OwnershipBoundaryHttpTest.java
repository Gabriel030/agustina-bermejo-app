package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

    static Stream<Resource> resources() {
        return Stream.of(Resource.values());
    }

    private long existingIdOf(Resource resource) {
        return switch (resource) {
            case CLOSURE_SHEET -> ownership.sectionId();
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
