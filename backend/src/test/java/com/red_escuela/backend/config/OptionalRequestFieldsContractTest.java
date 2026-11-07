package com.red_escuela.backend.config;

import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSubjectRequest;
import com.red_escuela.backend.announcement.dto.CreateAnnouncementRequest;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.enrollment.dto.RevokeEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalRequestFieldsContractTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static <T> T parseAndValidate(String json, Class<T> type) {
        T request = MAPPER.readValue(json, type);
        assertThat(VALIDATOR.validate(request)).isEmpty();
        return request;
    }

    @Test
    void createStaffRequestAcceptsMissingDocumentTypeAndAddress() {
        CreateStaffRequest request = parseAndValidate(
                "{\"nombre\":\"Ana\",\"apellido\":\"Diaz\",\"email\":\"ana@x.com\",\"dni\":\"123\",\"role\":\"TEACHER\"}",
                CreateStaffRequest.class);
        assertThat(request.documentType()).isNull();
        assertThat(request.street()).isNull();
    }

    @Test
    void updateStaffRequestAcceptsEmptyBody() {
        UpdateStaffRequest request = parseAndValidate("{}", UpdateStaffRequest.class);
        assertThat(request.email()).isNull();
        assertThat(request.telefono()).isNull();
    }

    @Test
    void yearBillingConfigurationRequestAcceptsEmptyBody() {
        YearBillingConfigurationRequest request = parseAndValidate("{}", YearBillingConfigurationRequest.class);
        assertThat(request.enrollmentFeeCount()).isNull();
        assertThat(request.blocksCertificates()).isNull();
    }

    @Test
    void addPrerequisiteRequestAcceptsMissingMinGradeAndNotes() {
        AddPrerequisiteRequest request = parseAndValidate(
                "{\"prerequisiteProgramSubjectId\":1,\"requirementType\":\"APPROVED\"}", AddPrerequisiteRequest.class);
        assertThat(request.minGrade()).isNull();
        assertThat(request.notes()).isNull();
    }

    @Test
    void updateSubjectRequestAcceptsEmptyBody() {
        UpdateSubjectRequest request = parseAndValidate("{}", UpdateSubjectRequest.class);
        assertThat(request.name()).isNull();
        assertThat(request.code()).isNull();
    }

    @Test
    void createSubjectRequestAcceptsOnlyNameAndCode() {
        CreateSubjectRequest request = parseAndValidate("{\"name\":\"Algebra\",\"code\":\"ALG\"}", CreateSubjectRequest.class);
        assertThat(request.description()).isNull();
        assertThat(request.gradeMode()).isNull();
    }

    @Test
    void createProgramSubjectRequestAcceptsOnlyMandatoryFields() {
        CreateProgramSubjectRequest request = parseAndValidate(
                "{\"subjectId\":1,\"category\":\"ELECTIVE\",\"credits\":4}", CreateProgramSubjectRequest.class);
        assertThat(request.curricularYear()).isNull();
        assertThat(request.electiveGroupId()).isNull();
    }

    @Test
    void createAcademicOfferingCycleRequestAcceptsProgramWithoutCourse() {
        CreateAcademicOfferingCycleRequest request = parseAndValidate(
                "{\"programId\":1,\"termId\":2}", CreateAcademicOfferingCycleRequest.class);
        assertThat(request.courseId()).isNull();
    }

    @Test
    void createAnnouncementRequestAcceptsMissingExpirationScheduleAndAudience() {
        CreateAnnouncementRequest request = parseAndValidate(
                "{\"title\":\"T\",\"body\":\"B\",\"category\":\"ADMIN\",\"urgent\":false}", CreateAnnouncementRequest.class);
        assertThat(request.scheduledFor()).isNull();
        assertThat(request.audience()).isNull();
    }

    @Test
    void revokeEnrollmentFeeExemptionRequestAcceptsMissingNotes() {
        RevokeEnrollmentFeeExemptionRequest request = parseAndValidate("{}", RevokeEnrollmentFeeExemptionRequest.class);
        assertThat(request.notes()).isNull();
    }
}
