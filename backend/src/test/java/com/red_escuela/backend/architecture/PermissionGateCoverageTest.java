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
            "billing");

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


    private static final Set<String> ACADEMICSTRUCTURE_ENDPOINTS_WITHOUT_GRID_RULE = Set.of(
            "AcademicOfferingCycleController#list",
            "AcademicOfferingCycleController#get",
            "AcademicOfferingCycleController#getSections",
            "ClassroomController#list",
            "ClassroomController#availability",
            "CourseController#list",
            "CourseController#get",
            "CourseController#getPricing",
            "CourseSectionController#listByCourseSubject",
            "CourseSectionController#get",
            "CourseSectionController#listInstructors",
            "CourseSubjectController#listByCourse",
            "CourseSubjectController#get",
            "ElectiveGroupController#list",
            "ElectiveGroupController#get",
            "ElectiveGroupController#listByProgram",
            "HolidayController#list",
            "MeTeachingSectionController#mine",
            "ProgramController#list",
            "ProgramController#get",
            "ProgramController#getPricing",
            "ProgramSubjectController#listByProgram",
            "ProgramSubjectController#get",
            "ProgramSubjectController#listPrerequisites",
            "ProgramSubjectController#listCorequisites",
            "ShiftRangeController#list",
            "SubjectController#list",
            "SubjectController#get",
            "SubjectSectionController#listBySubject",
            "SubjectSectionController#get",
            "SubjectSectionController#listInstructors",
            "TermController#list",
            "TermController#get");

    private static final Set<String> BILLING_B1_MIGRATED_ENDPOINTS = Set.of(
            "StudentPaymentController#registerCashPayment", "AnnualUpfrontCashPaymentController#registerAnnualUpfrontCashPayment",
            "BankTransferReceiptSubmissionController#submitReceiptOnBehalfOfStudent", "BankTransferReceiptController#approve",
            "BankTransferReceiptController#reject", "PaymentReversalController#revokeBankTransferApproval",
            "PaymentReversalController#registerChargeback", "PaymentReversalController#reverseChargeback",
            "RefundController#requestRefundOnBehalfOfStudent", "RefundController#completeRefund", "RefundController#failRefund",
            "CreditNoteController#issueOrRequestCreditNote", "InvoiceCancellationController#cancelOrRequestInvoiceCancellation",
            "CurrentAccountAllocationController#allocateCredit");

    private static final Set<String> BILLING_B1_SERVICE_METHODS_WITHOUT_ROLE_RULE = Set.of(
            ROOT + ".billing.payment.usecase.RegisterCashPaymentService#registerCashPayment",
            ROOT + ".billing.payment.usecase.RegisterAnnualUpfrontCashPaymentService#registerAnnualUpfrontCashPayment",
            ROOT + ".billing.payment.usecase.SubmitBankTransferReceiptService#submitReceiptOnBehalfOfStudent",
            ROOT + ".billing.payment.usecase.RevokeBankTransferApprovalService#revokeBankTransferApproval",
            ROOT + ".billing.payment.usecase.RegisterChargebackService#registerChargeback",
            ROOT + ".billing.payment.usecase.ReverseChargebackService#reverseChargeback",
            ROOT + ".billing.payment.usecase.RequestRefundService#requestRefundOnBehalfOfStudent",
            ROOT + ".billing.payment.usecase.CompleteRefundService#completeRefund",
            ROOT + ".billing.payment.usecase.FailRefundService#failRefund",
            ROOT + ".billing.note.usecase.IssueCreditNoteService#issueOrRequestCreditNote",
            ROOT + ".billing.note.usecase.CancelInvoiceService#cancelOrRequestInvoiceCancellation",
            ROOT + ".billing.account.usecase.AllocateAccountCreditService#allocateCreditOfStudentAccounts");

    private static final Set<String> BILLING_ENDPOINTS_MIGRATED_TO_THE_GRID = Set.of(
            "CollectionReminderImpactController#getImpact", "OverdueContactListController#list",
            "EnrollmentPaymentStandingController#getProgramEnrollmentStanding", "EnrollmentPaymentStandingController#getCourseEnrollmentStanding",
            "EnrollmentPaymentStandingController#listProgramEnrollmentStandingHistory", "EnrollmentPaymentStandingController#listCourseEnrollmentStandingHistory",
            "CollectionReminderScheduleController#listPolicies", "CollectionReminderScheduleController#listGroupVersions",
            "CollectionReminderScheduleController#listExceptionVersions", "CollectionReminderScheduleController#proposeGroupChange",
            "CollectionReminderScheduleController#validateGroupChange", "CollectionReminderScheduleController#proposeExceptionChange",
            "CollectionReminderScheduleController#validateExceptionChange", "CollectionReminderScheduleController#proposeExceptionRemoval",
            "CollectionReminderSettingsController#proposeOrPublishChange", "CollectionReminderSettingsController#validateChange",
            "CollectionReminderSettingsController#listVersions", "BillingMetricsController#getFirstMonth", "BillingMetricsController#getPanel",
            "StudentBillingOverviewController#billingOverview", "FinancialDashboardController#getDashboard", "FinancialDashboardController#getDebtorsPage",
            "BillingConfigurationAlertController#list", "LatePaymentSettingsController#current", "BillingRunController#list",
            "BillingRunController#find", "BillingRunController#listItems", "BillingRunAlertController#list",
            "MonthlyInvoiceRunController#list", "MonthlyInvoiceRunController#find", "MonthlyInvoiceRunController#listItems",
            "MonthlyInvoiceRunController#listDraftInvoiceGroups", "MonthlyInvoiceSimulationController#simulateMonthlyInvoices");

    private static final Set<String> BILLING_SERVICES_WITHOUT_ROLE_RULE = Set.of(
            ROOT + ".billing.reporting.usecase.GetMonthlyBillingMetricsService", ROOT + ".billing.reporting.usecase.GetFirstBillingMonthService",
            ROOT + ".billing.collections.usecase.GetEnrollmentPaymentStandingService", ROOT + ".billing.collections.usecase.ListOverdueContactsService",
            ROOT + ".billing.collections.usecase.GetCollectionReminderImpactService", ROOT + ".billing.configuration.usecase.ListReminderSchedulePoliciesService",
            ROOT + ".billing.configuration.usecase.ListReminderScheduleVersionsService", ROOT + ".billing.configuration.usecase.ProposeReminderScheduleChangeService",
            ROOT + ".billing.configuration.usecase.ValidateReminderScheduleChangeService", ROOT + ".billing.configuration.usecase.ProposeBillingConfigurationChangeService",
            ROOT + ".billing.configuration.usecase.ValidateBillingConfigurationChangeService", ROOT + ".billing.configuration.usecase.ListConfigurationVersionHistoryService",
            ROOT + ".billing.configuration.usecase.FindConfigurationVersionAuthorNamesService");

    private static final Set<String> BILLING_A2_MIGRATED_ENDPOINTS = Set.of(
            "AnnualUpfrontQuoteController#quoteForStaff", "AnnualUpfrontRequestViewController#listRequestsOfEnrollmentForStaff",
            "AnnualUpfrontRequestViewController#searchRequestsForStaff", "AnnualUpfrontRequestViewController#viewRequestForStaff",
            "AnnualUpfrontTermsController#listTermsVersions", "AuthorizationRequestController#get",
            "AuthorizationRequestController#list", "BankTransferReceiptController#file",
            "BankTransferReceiptController#listPending", "CreditNoteController#listCreditNotesOfStudent",
            "DiscountDefinitionController#findDiscountDefinition", "DiscountDefinitionController#listDiscountDefinitions",
            "DiscountDefinitionController#validateDiscountDefinition", "InvoiceCancellationController#previewInvoiceCancellation",
            "InvoiceController#byCourse", "InvoiceController#byProgram",
            "InvoiceController#priceBreakdown", "PaymentReversalPreviewController#previewApprovalRevocation",
            "PaymentReversalPreviewController#previewChargebackRegistration", "PaymentReversalPreviewController#previewChargebackReversal",
            "PriceController#getPriceChangeImpact", "PriceController#listPriceVersionHistory",
            "PriceController#listPrices", "PriceController#validatePriceChange",
            "ProgramYearBillingTemplateController#listBillingTemplates", "PromotionController#findActive",
            "PromotionController#lostBenefits", "RefundController#listRefundRequestsOfStudent",
            "RefundController#listRefundsOfStudent", "RefundPreviewController#describeRefundEligibilityOfStudent",
            "RefundPreviewController#previewRefundOnBehalfOfStudent", "StudentCreditBalanceController#describeCreditBalanceOfStudent",
            "StudentDiscountController#listForCourseEnrollment", "StudentDiscountController#listForProgramEnrollment",
            "StudentDiscountController#listForStudent", "StudentDiscountController#previewForCourseEnrollment",
            "StudentDiscountController#previewForProgramEnrollment", "StudentDiscountController#searchStudentDiscounts",
            "StudentPaymentController#listPaymentsOfStudent", "StudentPaymentController#previewCashPayment");

    private static final Set<String> BILLING_A2_SERVICE_METHODS_WITHOUT_ROLE_RULE = Set.of(
            "annualupfront.usecase.ViewAnnualUpfrontRequestService#viewAnnualUpfrontRequestForStaff", "annualupfront.usecase.ViewAnnualUpfrontRequestService#listAnnualUpfrontRequestsOfEnrollmentForStaff",
            "annualupfront.usecase.ViewAnnualUpfrontRequestService#searchAnnualUpfrontRequestsForStaff", "annualupfront.usecase.QuoteAnnualUpfrontService#quoteAnnualUpfrontForStaff",
            "annualupfront.usecase.ViewAnnualUpfrontTermsService#listAnnualUpfrontTermsVersions", "payment.usecase.PreviewRefundService#previewRefundOnBehalfOfStudent",
            "payment.usecase.PreviewRefundService#describeRefundEligibilityOfStudent", "payment.usecase.ListStudentRefundsService#listRefundsOfStudent",
            "payment.usecase.ListStudentRefundsService#listRefundRequestsOfStudent", "payment.usecase.DescribeStudentCreditBalanceService#describeCreditBalanceOfStudent",
            "payment.usecase.PreviewPaymentReversalService#previewChargebackRegistration", "payment.usecase.PreviewPaymentReversalService#previewChargebackReversal",
            "payment.usecase.PreviewPaymentReversalService#previewApprovalRevocation", "payment.usecase.ListStudentPaymentsService#describePaymentsOfStudent",
            "payment.usecase.PreviewCashPaymentService#previewCashPayment", "note.usecase.ListCreditNotesOfStudentWithAuthorshipService#listCreditNotesOfStudentWithAuthorship",
            "note.usecase.PreviewInvoiceCancellationService#previewInvoiceCancellation", "discount.usecase.ListActivePromotionsByLegacyContractService#listActivePromotions",
            "discount.usecase.ListStudentDiscountsOfStudentService#listStudentDiscountsOfStudent", "discount.usecase.SearchStudentDiscountsService#searchStudentDiscounts",
            "discount.usecase.ListDiscountDefinitionsService#listDiscountDefinitions", "discount.usecase.ListStudentDiscountsService#listStudentDiscounts",
            "discount.usecase.FindLostCohortPromotionBenefitsService#findLostBenefits", "discount.usecase.ValidateDiscountDefinitionService#validateDiscountDefinition",
            "discount.usecase.PreviewStudentDiscountGrantService#previewStudentDiscountGrant", "discount.usecase.FindDiscountDefinitionService#findDiscountDefinition",
            "pricing.usecase.ListPricesService#listPrices", "pricing.usecase.GetPriceChangeImpactService#getPriceChangeImpact",
            "pricing.usecase.ListPriceVersionHistoryService#listPriceVersionHistory", "pricing.usecase.ValidatePriceChangeService#validatePriceChange");

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
                if (ENROLLMENT_ENDPOINTS_WITHOUT_ROLE_RULE.contains(endpoint) || ENROLLMENT_ENDPOINTS_FIXED_TO_DIRECTOR.contains(endpoint) || ACADEMICSTRUCTURE_ENDPOINTS_WITHOUT_GRID_RULE.contains(endpoint)) {
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
    void academicstructureEndpointsExemptFromTheGridKeepOnlyTheirFixedRule() throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> declared = new HashSet<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                declared.add(endpoint);
                if (!ACADEMICSTRUCTURE_ENDPOINTS_WITHOUT_GRID_RULE.contains(endpoint)) {
                    continue;
                }
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class) != null) {
                    violations.add(endpoint + " must not use the grid");
                }
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (preAuthorize != null && !Set.of("isAuthenticated()", "hasRole('DOCENTE')").contains(preAuthorize.value())) {
                    violations.add(endpoint + " carries an unexpected rule: " + preAuthorize.value());
                }
            }
        }
        assertThat(violations).isEmpty();
        assertThat(declared).containsAll(ACADEMICSTRUCTURE_ENDPOINTS_WITHOUT_GRID_RULE);
    }

    @Test
    void billingEndpointsMigratedToTheGridRequireTheGateAndTheirServicesCarryNoRoleRule() throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> declared = new HashSet<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                if (!BILLING_ENDPOINTS_MIGRATED_TO_THE_GRID.contains(endpoint) || !moduleOf(controller).equals("billing")) {
                    continue;
                }
                declared.add(endpoint);
                RequiresPermission requiresPermission = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (requiresPermission == null || !requiresPermission.code().startsWith("billing.") || !usesGate(method, controller)
                        || preAuthorize == null || !preAuthorize.value().contains(GATE_EXPRESSION)) {
                    violations.add(endpoint + " must be guarded by @RequiresPermission");
                }
            }
        }
        assertThat(declared).containsExactlyInAnyOrderElementsOf(BILLING_ENDPOINTS_MIGRATED_TO_THE_GRID);
        for (String serviceName : BILLING_SERVICES_WITHOUT_ROLE_RULE) {
            Class<?> service = Class.forName(serviceName);
            if (AnnotatedElementUtils.findMergedAnnotation(service, PreAuthorize.class) != null) {
                violations.add(service.getSimpleName() + " must not carry a role rule");
            }
            for (Method method : service.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) != null) {
                    violations.add(service.getSimpleName() + "#" + method.getName() + " must not carry a role rule");
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void billingA2EndpointsUseTheGridGate() throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> found = new HashSet<>();
        for (Class<?> controller : controllers()) {
            if (!moduleOf(controller).equals("billing")) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                if (!BILLING_A2_MIGRATED_ENDPOINTS.contains(endpoint)) {
                    continue;
                }
                found.add(endpoint);
                RequiresPermission gate = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
                if (gate == null || !gate.code().startsWith("billing.")) {
                    violations.add(endpoint + " must carry @RequiresPermission with a billing code");
                }
            }
        }
        assertThat(violations).isEmpty();
        assertThat(found).isEqualTo(BILLING_A2_MIGRATED_ENDPOINTS);
    }

    @Test
    void billingA2ServiceMethodsCarryNoRoleRule() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String entry : BILLING_A2_SERVICE_METHODS_WITHOUT_ROLE_RULE) {
            String[] parts = entry.split("#");
            Class<?> service = Class.forName(ROOT + ".billing." + parts[0]);
            if (AnnotatedElementUtils.findMergedAnnotation(service, PreAuthorize.class) != null) {
                violations.add(service.getSimpleName() + " must not carry a class-level role rule");
            }
            List<Method> methods = Stream.of(service.getDeclaredMethods()).filter(method -> method.getName().equals(parts[1])).toList();
            assertThat(methods).as(entry).isNotEmpty();
            for (Method method : methods) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) != null) {
                    violations.add(entry + " must not carry a role rule");
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void billingB1EndpointsRequireTheGateAndTheStaffServiceMethodsCarryNoRoleRule() throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> declared = new HashSet<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                if (!BILLING_B1_MIGRATED_ENDPOINTS.contains(endpoint) || !moduleOf(controller).equals("billing")) {
                    continue;
                }
                declared.add(endpoint);
                RequiresPermission requiresPermission = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (requiresPermission == null || !requiresPermission.code().startsWith("billing.") || !usesGate(method, controller)
                        || preAuthorize == null || !preAuthorize.value().contains(GATE_EXPRESSION)) {
                    violations.add(endpoint + " must be guarded by @RequiresPermission");
                }
            }
        }
        assertThat(declared).containsExactlyInAnyOrderElementsOf(BILLING_B1_MIGRATED_ENDPOINTS);
        for (String serviceMethod : BILLING_B1_SERVICE_METHODS_WITHOUT_ROLE_RULE) {
            String[] parts = serviceMethod.split("#");
            Class<?> service = Class.forName(parts[0]);
            if (AnnotatedElementUtils.findMergedAnnotation(service, PreAuthorize.class) != null) {
                violations.add(service.getSimpleName() + " must not carry a role rule");
            }
            List<Method> methods = Stream.of(service.getDeclaredMethods()).filter(method -> method.getName().equals(parts[1])).toList();
            assertThat(methods).as(serviceMethod).isNotEmpty();
            for (Method method : methods) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) != null) {
                    violations.add(service.getSimpleName() + "#" + method.getName() + " must not carry a role rule");
                }
            }
        }
        assertThat(violations).isEmpty();
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
