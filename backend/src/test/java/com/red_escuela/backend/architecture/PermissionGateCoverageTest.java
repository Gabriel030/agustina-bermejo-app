package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionGateCoverageTest {

    private static final String ROOT = "com.red_escuela.backend";
    private static final String GATE_EXPRESSION = "@permissionGate.allows(";

    private static final Set<String> MODULES_PENDING_MIGRATION = Set.of(
            "academicstructure", "billing");

    private static final String LEGAL_DOCUMENT_CONTROLLER = ROOT + ".identity.controller.LegalDocumentController";

    private static final Set<String> CONTROLLERS_FIXED_BY_ROLE = Set.of(
            ROOT + ".identity.controller.PermissionController",
            ROOT + ".platform.controller.BackupStatusController",
            ROOT + ".platform.controller.MyPaymentMethodsController",
            ROOT + ".platform.controller.PlatformAuditLogController",
            ROOT + ".platform.controller.PlatformFeatureController",
            ROOT + ".platform.controller.PlatformProvisioningController",
            ROOT + ".evaluation.controller.MeSectionEvaluationConfigController");

    private static final Set<String> CONTROLLERS_WITHOUT_ROLE_RULE = Set.of(
            ROOT + ".announcement.controller.MeAnnouncementController",
            ROOT + ".identity.controller.AuthController",
            ROOT + ".identity.controller.CancelEmailChangeController",
            ROOT + ".identity.controller.InstitutionController",
            ROOT + ".identity.controller.OwnerEmailChangeApprovalController",
            ROOT + ".identity.controller.PersonController",
            ROOT + ".identity.controller.VerifyEmailController",
            ROOT + ".platform.controller.ModulesController",
            ROOT + ".notification.controller.MeNotificationController",
            ROOT + ".notification.broadcast.controller.ContactConsentController",
            ROOT + ".notification.broadcast.controller.PublicUnsubscribeController",
            ROOT + ".graduation.controller.MeCertificateController",
            ROOT + ".graduation.controller.MeGraduationRequestController",
            ROOT + ".graduation.certificate.controller.MeGraduationCertificateController",
            ROOT + ".graduation.certificate.regular.controller.MeRegularCertificateController",
            ROOT + ".graduation.certificate.approvedsubjects.controller.MeApprovedSubjectsCertificateController",
            ROOT + ".graduation.procedure.controller.MeProcedureController",
            ROOT + ".graduation.procedure.controller.MeOwnDocumentController",
            ROOT + ".evaluation.controller.MeExamBoardRegistrationController",
            ROOT + ".graduation.certificate.verification.controller.PublicCertificateController",
            ROOT + ".graduation.certificate.verification.controller.PublicDocumentController",
            ROOT + ".enrollment.controller.MeCourseAdmissionPreviewController",
            ROOT + ".enrollment.controller.MeCourseEnrollmentController",
            ROOT + ".enrollment.controller.MeEnrollmentChangeController",
            ROOT + ".enrollment.controller.MeEnrollmentController",
            ROOT + ".enrollment.controller.ProgramEnrollmentApplicationWithDocumentsController",
            ROOT + ".academicrecord.controller.MeAcademicRecordController",
            ROOT + ".academicrecord.controller.MeCalendarFeedController",
            ROOT + ".academicrecord.controller.MeCourseEnrollmentDetailController",
            ROOT + ".academicrecord.controller.MeCourseSectionEnrollmentController",
            ROOT + ".academicrecord.controller.PublicCalendarFeedController");

    private static final Set<String> ENROLLMENT_ENDPOINTS_WITHOUT_ROLE_RULE = Set.of(
            "CurriculumProgressController#myCurriculumProgress", "CurriculumProgressController#myGraduationProjection",
            "EnrollmentFeeController#myStatus", "ProgramEnrollmentApplicationController#request",
            "ProgramEnrollmentApplicationController#mine", "ProgramEnrollmentApplicationController#addDocument",
            "ProgramEnrollmentApplicationController#myDocuments", "ProspectController#createProspect");

    private static final Set<String> ENROLLMENT_ENDPOINTS_FIXED_TO_DIRECTOR = Set.of(
            "DirectorApprovalRequestController#list", "DirectorApprovalRequestController#get",
            "DirectorApprovalRequestController#approve", "DirectorApprovalRequestController#reject");


    private static final Set<String> CONTROLLERS_PENDING_DOCUMENT_CONSTANCIAS = Set.of(
            ROOT + ".graduation.certificate.exam.controller.ExamCertificateController",
            ROOT + ".graduation.certificate.exam.controller.ExamBoardCertificateController",
            ROOT + ".graduation.certificate.approvedsubjects.controller.ApprovedSubjectsCertificateController",
            ROOT + ".graduation.certificate.regular.controller.RegularCertificateController",
            ROOT + ".graduation.certificate.regular.controller.RegularCertificateRequestController",
            ROOT + ".graduation.certificate.regular.controller.CertificateExceptionController",
            ROOT + ".graduation.certificate.regular.controller.RegularCertificateSettingsController",
            ROOT + ".graduation.certificate.template.controller.DocumentCertificateTemplateController",
            ROOT + ".graduation.certificate.signatory.controller.CertificateSignatoryController",
            ROOT + ".graduation.documentsettings.controller.DocumentIssuanceSettingsController");

    private static final Set<String> ENDPOINTS_FIXED_BY_CONDITIONAL_ROLE_RULE = Set.of(
            "CertificateTemplateController#preview");


    private static final Set<String> LEGAL_DOCUMENT_ENDPOINTS_WITHOUT_ROLE_RULE = Set.of(
            "getCurrentDocuments", "getPendingAcceptances", "acceptCurrentDocuments", "confirmGuardianAcceptance",
            "resendGuardianConfirmation", "getNewsletterPreference", "updateNewsletterPreference");

    @Test
    void everyProtectedEndpointUsesTheGateOrIsExempt() throws Exception {
        List<String> uncovered = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            if (CONTROLLERS_FIXED_BY_ROLE.contains(controller.getName()) || CONTROLLERS_WITHOUT_ROLE_RULE.contains(controller.getName()) || CONTROLLERS_PENDING_DOCUMENT_CONSTANCIAS.contains(controller.getName()) || MODULES_PENDING_MIGRATION.contains(moduleOf(controller))) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                if (ENDPOINTS_FIXED_BY_CONDITIONAL_ROLE_RULE.contains(controller.getSimpleName() + "#" + method.getName())) {
                    continue;
                }
                if (controller.getName().equals(LEGAL_DOCUMENT_CONTROLLER) && LEGAL_DOCUMENT_ENDPOINTS_WITHOUT_ROLE_RULE.contains(method.getName())) {
                    continue;
                }
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                if (ENROLLMENT_ENDPOINTS_WITHOUT_ROLE_RULE.contains(endpoint) || ENROLLMENT_ENDPOINTS_FIXED_TO_DIRECTOR.contains(endpoint)) {
                    continue;
                }
                if (!usesGate(method, controller)) {
                    uncovered.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(uncovered).as("endpoints without the permission gate and outside the exempt lists").isEmpty();
    }

    @Test
    void permissionEndpointsStayFixedToTheDirectorRoleAndNeverUseTheGrid() throws Exception {
        Class<?> controller = Class.forName(ROOT + ".identity.controller.PermissionController");
        int checked = 0;
        for (Method method : controller.getDeclaredMethods()) {
            if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                continue;
            }
            PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            assertThat(preAuthorize).as(method.getName()).isNotNull();
            String expectedRule = Set.of("getCatalog", "getGrid").contains(method.getName())
                    ? "hasAnyRole('DIRECTOR', 'SUBDIRECTOR')"
                    : "hasRole('DIRECTOR')";
            assertThat(preAuthorize.value()).isEqualTo(expectedRule);
            checked++;
        }
        assertThat(checked).isEqualTo(6);
    }

    @Test
    void controllersExemptAsWithoutRoleRuleCarryNoRoleAnnotations() throws Exception {
        List<String> annotated = new ArrayList<>();
        for (String controllerName : CONTROLLERS_WITHOUT_ROLE_RULE) {
            Class<?> controller = Class.forName(controllerName);
            if (AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class) != null
                    || AnnotatedElementUtils.findMergedAnnotation(controller, RequiresPermission.class) != null) {
                annotated.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) != null
                        || AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class) != null) {
                    annotated.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(annotated).as("exempt controllers must not carry role rules").isEmpty();
    }

    @Test
    void enrollmentEndpointsExemptFromTheGridKeepTheirFixedRule() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (ENROLLMENT_ENDPOINTS_WITHOUT_ROLE_RULE.contains(endpoint) && preAuthorize != null) {
                    violations.add(endpoint + " must not carry a role rule");
                }
                if (ENROLLMENT_ENDPOINTS_FIXED_TO_DIRECTOR.contains(endpoint)
                        && (preAuthorize == null || !preAuthorize.value().equals("hasRole('DIRECTOR')"))) {
                    violations.add(endpoint + " must stay fixed to the Director role");
                }
            }
        }
        assertThat(violations).isEmpty();
        Set<String> declared = new HashSet<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                declared.add(controller.getSimpleName() + "#" + method.getName());
            }
        }
        assertThat(declared).containsAll(ENROLLMENT_ENDPOINTS_WITHOUT_ROLE_RULE).containsAll(ENROLLMENT_ENDPOINTS_FIXED_TO_DIRECTOR);
    }

    @Test
    void exemptListsOnlyNameExistingModulesAndControllers() throws Exception {
        for (String controllerName : Stream.of(CONTROLLERS_FIXED_BY_ROLE, CONTROLLERS_WITHOUT_ROLE_RULE, CONTROLLERS_PENDING_DOCUMENT_CONSTANCIAS).flatMap(Set::stream).toList()) {
            assertThat(Class.forName(controllerName)).isNotNull();
        }
        Set<String> present = new HashSet<>();
        controllers().forEach(controller -> present.add(moduleOf(controller)));
        assertThat(present).containsAll(MODULES_PENDING_MIGRATION);
    }

    @Test
    void evaluationClassesOutsideControllersCarryNoRoleAnnotationsThatWouldOverrideTheGrid() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        List<String> annotated = new ArrayList<>();
        int scanned = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT + ".evaluation")) {
            Class<?> type = Class.forName(definition.getBeanClassName(), false, PermissionGateCoverageTest.class.getClassLoader());
            boolean fromMainClasses = type.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("/classes/");
            if (!fromMainClasses || type.isAnnotationPresent(RestController.class)) {
                continue;
            }
            scanned++;
            if (AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class) != null) {
                annotated.add(type.getSimpleName());
            }
            for (Method method : type.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) != null) {
                    annotated.add(type.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(scanned).isPositive();
        assertThat(annotated).as("service-level role rules in evaluation override the grid in ENFORCE").isEmpty();
    }

    private static boolean usesGate(Method method, Class<?> controller) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        PreAuthorize effective = onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        return effective != null && effective.value().contains(GATE_EXPRESSION);
    }

    private static String moduleOf(Class<?> type) {
        return type.getPackageName().substring(ROOT.length() + 1).split("\\.")[0];
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            boolean fromMainClasses = type.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("/classes/");
            if (fromMainClasses && type.getPackageName().startsWith(ROOT + ".")) {
                classes.add(type);
            }
        }
        return classes;
    }
}
