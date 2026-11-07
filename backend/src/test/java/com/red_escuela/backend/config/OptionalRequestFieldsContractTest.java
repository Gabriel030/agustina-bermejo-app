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
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.announcement.dto.AudienceSpecRequest;
import com.red_escuela.backend.billing.collections.dto.CollectionReminderImpactRequest;
import com.red_escuela.backend.billing.configuration.dto.ChangeReminderScheduleRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.SaveProspectDraftRequest;
import com.red_escuela.backend.evaluation.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRegistrationRequest;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
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

    @Test
    void updateProgramRequestAcceptsNullForEveryField() {
        UpdateProgramRequest request = parseAndValidate(
                "{\"name\":null,\"totalYears\":null,\"price\":null,\"enrollmentFeeAmount\":null,\"totalCreditsRequired\":null,"
                        + "\"totalHoursRequired\":null,\"teachingMode\":null,\"level\":null,\"graduationProfile\":null,\"admissionRequirements\":null}",
                UpdateProgramRequest.class);
        assertThat(request.getName()).isNull();
        assertThat(request.getPrice()).isNull();
        assertThat(request.getEnrollmentFeeAmount()).isNull();
    }

    @Test
    void saveProspectDraftRequestAcceptsNullForEveryField() {
        SaveProspectDraftRequest request = parseAndValidate(
                "{\"dni\":null,\"documentType\":null,\"street\":null,\"number\":null,\"floor\":null,\"city\":null,\"state\":null,"
                        + "\"postalCode\":null,\"country\":null,\"birthDate\":null,\"sex\":null,\"nationality\":null,\"guardian\":null,\"emergencyContact\":null}",
                SaveProspectDraftRequest.class);
        assertThat(request.floor()).isNull();
        assertThat(request.documentType()).isNull();
    }

    @Test
    void saveProspectDraftRequestAcceptsEmptyBody() {
        SaveProspectDraftRequest request = parseAndValidate("{}", SaveProspectDraftRequest.class);
        assertThat(request.country()).isNull();
    }

    @Test
    void createProspectRequestAcceptsMissingPhone() {
        CreateProspectRequest request = parseAndValidate(
                "{\"nombre\":\"Ana\",\"apellido\":\"Diaz\",\"email\":\"ana@x.com\",\"institutionId\":1}", CreateProspectRequest.class);
        assertThat(request.telefono()).isNull();
    }

    @Test
    void createProspectRequestAcceptsNullPhone() {
        CreateProspectRequest request = parseAndValidate(
                "{\"nombre\":\"Ana\",\"apellido\":\"Diaz\",\"email\":\"ana@x.com\",\"telefono\":null,\"institutionId\":1}", CreateProspectRequest.class);
        assertThat(request.telefono()).isNull();
    }

    @Test
    void createExamBoardRegistrationRequestAcceptsOnlyProgramEnrollment() {
        CreateExamBoardRegistrationRequest request = parseAndValidate(
                "{\"examBoardId\":1,\"programSectionEnrollmentId\":2,\"courseSectionEnrollmentId\":null}", CreateExamBoardRegistrationRequest.class);
        assertThat(request.courseSectionEnrollmentId()).isNull();
    }

    @Test
    void createExamBoardRegistrationRequestAcceptsOnlyCourseEnrollment() {
        CreateExamBoardRegistrationRequest request = parseAndValidate(
                "{\"examBoardId\":1,\"courseSectionEnrollmentId\":3}", CreateExamBoardRegistrationRequest.class);
        assertThat(request.programSectionEnrollmentId()).isNull();
    }

    @Test
    void updateSectionEvaluationConfigRequestAcceptsNullForEveryField() {
        UpdateSectionEvaluationConfigRequest request = parseAndValidate(
                "{\"tpCount\":null,\"tp1DueDate\":null,\"tp2DueDate\":null,\"tp3DueDate\":null,\"exam1Date\":null,\"exam2Date\":null,"
                        + "\"exam3Date\":null,\"exam4Date\":null,\"finalCall1Date\":null,\"finalCall2Date\":null}",
                UpdateSectionEvaluationConfigRequest.class);
        assertThat(request.tpCount()).isNull();
        assertThat(request.finalCall2Date()).isNull();
    }

    @Test
    void updateSectionEvaluationConfigRequestAcceptsEmptyBody() {
        UpdateSectionEvaluationConfigRequest request = parseAndValidate("{}", UpdateSectionEvaluationConfigRequest.class);
        assertThat(request.exam1Date()).isNull();
    }

    @Test
    void audienceSpecRequestAcceptsNullInstancesQuantifierAndThreshold() {
        AudienceSpecRequest request = parseAndValidate(
                "{\"key\":\"ALL\",\"instances\":null,\"quantifier\":null,\"threshold\":null}", AudienceSpecRequest.class);
        assertThat(request.instances()).isNull();
        assertThat(request.threshold()).isNull();
    }

    @Test
    void createAnnouncementRequestAcceptsExplicitNullExpirationScheduleAndAudience() {
        CreateAnnouncementRequest request = parseAndValidate(
                "{\"title\":\"T\",\"body\":\"B\",\"category\":\"ADMIN\",\"urgent\":false,\"expiresAt\":null,\"scheduledFor\":null,\"audience\":null}",
                CreateAnnouncementRequest.class);
        assertThat(request.expiresAt()).isNull();
        assertThat(request.audience()).isNull();
    }

    @Test
    void changeReminderScheduleRequestAcceptsNullChangeReasonOverridesAndNoticeOptionals() {
        ChangeReminderScheduleRequest request = parseAndValidate(
                "{\"effectiveFrom\":\"2026-12-01\",\"changeReason\":null,\"schedule\":{\"followsGroupSchedule\":null,\"sendingOverrides\":null,"
                        + "\"notices\":[{\"noticeKey\":null,\"messageType\":\"ISSUANCE_NOTICE\",\"milestone\":\"ISSUANCE\",\"offsetDays\":0,\"channels\":null,\"repetition\":null}]}}",
                ChangeReminderScheduleRequest.class);
        assertThat(request.changeReason()).isNull();
        assertThat(request.schedule().sendingOverrides()).isNull();
        assertThat(request.schedule().notices().get(0).repetition()).isNull();
    }

    @Test
    void changeReminderScheduleRequestAcceptsNullSendingOverrideValues() {
        ChangeReminderScheduleRequest request = parseAndValidate(
                "{\"effectiveFrom\":\"2026-12-01\",\"schedule\":{\"notices\":[],\"sendingOverrides\":{\"suspensionNoticeEnabled\":null,"
                        + "\"postWithdrawalSummaryMonths\":null,\"emailWindowStart\":null,\"emailWindowEnd\":null,\"dailyEmailCap\":null,"
                        + "\"contactListDaysOverdue\":null,\"contactListOverdueInstallments\":null}}}",
                ChangeReminderScheduleRequest.class);
        assertThat(request.schedule().sendingOverrides().dailyEmailCap()).isNull();
    }

    @Test
    void collectionReminderImpactRequestAcceptsNullGroupTargetScheduleForGlobalSettingsOnlyChange() {
        CollectionReminderImpactRequest request = parseAndValidate(
                "{\"effectiveFrom\":\"2026-12-01\",\"group\":null,\"targetId\":null,\"schedule\":null,\"globalSettings\":{"
                        + "\"suspensionNoticeEnabled\":true,\"postWithdrawalSummaryMonths\":3,\"emailWindowStart\":\"08:00\",\"emailWindowEnd\":\"20:00\","
                        + "\"dailyEmailCap\":2,\"contactListDaysOverdue\":10,\"contactListOverdueInstallments\":2,\"dailyDigestEnabled\":null}}",
                CollectionReminderImpactRequest.class);
        assertThat(request.group()).isNull();
        assertThat(request.schedule()).isNull();
        assertThat(request.globalSettings().dailyDigestEnabled()).isNull();
    }

    @Test
    void createEvaluationSchemeRequestAcceptsNullFinalWeightAggregationPromotionAndNotes() {
        CreateEvaluationSchemeRequest request = parseAndValidate(
                "{\"name\":\"S\",\"examCountExpected\":2,\"hasFinal\":true,\"finalWeight\":null,\"aggregationMethod\":null,"
                        + "\"minRegularGrade\":4,\"minApprovalGrade\":6,\"promotionMinGrade\":null,\"notes\":null}",
                CreateEvaluationSchemeRequest.class);
        assertThat(request.finalWeight()).isNull();
        assertThat(request.aggregationMethod()).isNull();
        assertThat(request.notes()).isNull();
    }

    @Test
    void revokeEnrollmentFeeExemptionRequestAcceptsExplicitNullNotes() {
        RevokeEnrollmentFeeExemptionRequest request = parseAndValidate("{\"notes\":null}", RevokeEnrollmentFeeExemptionRequest.class);
        assertThat(request.notes()).isNull();
    }
}
