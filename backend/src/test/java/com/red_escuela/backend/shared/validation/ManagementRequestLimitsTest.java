package com.red_escuela.backend.shared.validation;

import com.red_escuela.backend.academicstructure.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.enrollment.dto.SaveProspectDraftRequest;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManagementRequestLimitsTest {

    private static final String LONG_TEXT = "x".repeat(256);
    private static final String LONG_DESCRIPTION = "x".repeat(501);

    @RestController
    static class EchoController {
        @PostMapping("/echo/create-section")
        void createSection(@RequestBody CreateSectionRequest request) {
        }

        @PostMapping("/echo/update-section")
        void updateSection(@RequestBody UpdateSectionRequest request) {
        }

        @PostMapping("/echo/update-course-subject")
        void updateCourseSubject(@RequestBody UpdateCourseSubjectRequest request) {
        }

        @PostMapping("/echo/create-elective-group")
        void createElectiveGroup(@RequestBody CreateElectiveGroupRequest request) {
        }

        @PostMapping("/echo/update-elective-group")
        void updateElectiveGroup(@RequestBody UpdateElectiveGroupRequest request) {
        }

        @PostMapping("/echo/create-term")
        void createTerm(@RequestBody CreateTermRequest request) {
        }

        @PostMapping("/echo/update-term")
        void updateTerm(@RequestBody UpdateTermRequest request) {
        }

        @PostMapping("/echo/update-program-subject")
        void updateProgramSubject(@RequestBody UpdateProgramSubjectRequest request) {
        }

        @PostMapping("/echo/create-grade-item")
        void createGradeItem(@RequestBody CreateGradeItemRequest request) {
        }

        @PostMapping("/echo/save-prospect-draft")
        void saveProspectDraft(@RequestBody SaveProspectDraftRequest request) {
        }

        @PostMapping("/echo/notification-preferences")
        void updateNotificationPreferences(@RequestBody UpdateNotificationPreferencesRequest request) {
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static String manyPreferences() {
        return IntStream.range(0, 51).mapToObj(i -> "\"k" + i + "\":{\"push\":true}").collect(Collectors.joining(",", "{\"preferences\":{", "}}"));
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("/echo/create-section", "{\"name\":\"" + LONG_TEXT + "\",\"termId\":1,\"capacity\":10}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/create-section", "{\"name\":\"A\",\"termId\":1,\"capacity\":1000000}", "REQUEST_FIELD_OUT_OF_RANGE"),
                Arguments.of("/echo/update-section", "{\"schedule\":\"" + LONG_TEXT + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/update-section", "{\"capacity\":-5}", "REQUEST_FIELD_OUT_OF_RANGE"),
                Arguments.of("/echo/update-course-subject", "{\"name\":\"" + LONG_TEXT + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/update-course-subject", "{\"weeklyWorkload\":100000}", "REQUEST_FIELD_OUT_OF_RANGE"),
                Arguments.of("/echo/create-elective-group", "{\"name\":\"A\",\"description\":\"" + LONG_DESCRIPTION + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/update-elective-group", "{\"description\":\"" + LONG_DESCRIPTION + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/create-term", "{\"year\":2026,\"description\":\"A\",\"type\":\"ANNUAL\",\"startDate\":\"2026-12-01T00:00:00\",\"endDate\":\"2026-03-01T00:00:00\"}", "REQUEST_DATE_RANGE_INVALID"),
                Arguments.of("/echo/update-term", "{\"startDate\":\"2026-12-01T00:00:00\",\"endDate\":\"2026-03-01T00:00:00\"}", "REQUEST_DATE_RANGE_INVALID"),
                Arguments.of("/echo/update-term", "{\"enrollmentStart\":\"2026-12-01T00:00:00\",\"enrollmentEnd\":\"2026-03-01T00:00:00\"}", "REQUEST_DATE_RANGE_INVALID"),
                Arguments.of("/echo/update-term", "{\"description\":\"" + LONG_TEXT + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/update-program-subject", "{\"credits\":-1}", "REQUEST_FIELD_OUT_OF_RANGE"),
                Arguments.of("/echo/create-grade-item", "{\"type\":\"PARTIAL\",\"date\":\"2026-03-01T00:00:00\",\"score\":7,\"comments\":\"" + LONG_TEXT + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/create-grade-item", "{\"type\":\"PARTIAL\",\"date\":\"2026-03-01T00:00:00\",\"score\":7,\"sequenceNumber\":2000000000}", "REQUEST_FIELD_OUT_OF_RANGE"),
                Arguments.of("/echo/save-prospect-draft", "{\"street\":\"" + LONG_TEXT + "\"}", "REQUEST_FIELD_TOO_LONG"),
                Arguments.of("/echo/notification-preferences", manyPreferences(), "REQUEST_LIST_TOO_LARGE"),
                Arguments.of("/echo/notification-preferences", "{\"preferences\":{\"" + LONG_TEXT + "\":{\"push\":true}}}", "REQUEST_FIELD_TOO_LONG"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidManagementBodyIsRejectedWithStableCode(String path, String body, String code) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private static Stream<Arguments> validBodies() {
        return Stream.of(
                Arguments.of("/echo/create-section", "{\"name\":\"" + "x".repeat(255) + "\",\"termId\":1,\"capacity\":10000}"),
                Arguments.of("/echo/update-term", "{\"startDate\":\"2026-03-01T00:00:00\",\"endDate\":\"2026-03-01T00:00:00\"}"),
                Arguments.of("/echo/update-elective-group", "{\"description\":\"" + "x".repeat(500) + "\"}"),
                Arguments.of("/echo/create-grade-item", "{\"type\":\"PARTIAL\",\"date\":\"2026-03-01T00:00:00\",\"score\":7,\"sequenceNumber\":1000}"));
    }

    @ParameterizedTest
    @MethodSource("validBodies")
    void bodyAtTheLimitIsAccepted(String path, String body) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void emptyUpdateBodyIsAccepted() throws Exception {
        mockMvc.perform(post("/echo/update-section").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }
}
