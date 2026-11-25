package com.red_escuela.backend.graduation.debtexception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocument;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.api.DebtExceptionGate;
import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionExpirer;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(DocumentDebtExceptionIT.FakeIssuerConfig.class)
@ActiveProfiles("test")
class DocumentDebtExceptionIT extends AbstractIntegrationTest {

    private static final AtomicBoolean FAIL_RENDER = new AtomicBoolean();
    private static final AtomicReference<String> AUDIT_DETAIL = new AtomicReference<>("documentId=1");

    @TestConfiguration
    static class FakeIssuerConfig {

        @Bean
        DebtExceptionDocumentIssuer fakeExamIssuer() {
            return new DebtExceptionDocumentIssuer() {
                @Override
                public boolean supports(CertificateDocumentType type) {
                    return type == CertificateDocumentType.EXAM_CERTIFICATE;
                }

                @Override
                public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
                    if (Long.valueOf(404L).equals(scope.registrationId())) {
                        throw new DebtExceptionNotFoundException();
                    }
                }

                @Override
                public DebtExceptionDocument render(DebtExceptionScope scope) {
                    if (FAIL_RENDER.get()) {
                        throw new UncheckedIOException(new IOException("render failed"));
                    }
                    return new DebtExceptionDocument("EXC-" + scope.exceptionId(), "pdf".getBytes(), AUDIT_DETAIL.get());
                }
            };
        }
    }

    private static final String REASON = "Convenio de pago firmado con la familia del alumno";

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private DebtExceptionGate gate;
    @Autowired
    private DebtExceptionExpirer expirer;
    @Autowired
    private DocumentDebtExceptionRepository repository;

    @Autowired
    private PermissionSnapshotCache permissionSnapshotCache;

    private Long studentId;
    private Long otherStudentId;
    private Person administrative;
    private String administrativeToken;
    private String directorToken;
    private String subdirectorToken;
    private String studentToken;

    @BeforeEach
    void seedStudentsAndStaff() {
        jdbc.update("insert into permission_module_mode (role, module, mode) values ('SUBDIRECTOR', 'document', 'ENFORCE') on conflict (role, module) do update set mode = 'ENFORCE'");
        permissionSnapshotCache.invalidate();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        transactions.executeWithoutResult(status -> {
            studentId = persistStudent().getId();
            otherStudentId = persistStudent().getId();
            administrative = persistPerson("ADMINISTRATIVO");
            administrativeToken = jwtService.generateAccessToken(administrative);
            directorToken = jwtService.generateAccessToken(persistPerson("DIRECTOR"));
            subdirectorToken = jwtService.generateAccessToken(persistPerson("SUBDIRECTOR"));
            studentToken = jwtService.generateAccessToken(persistPerson("ALUMNO"));
        });
        actAs(administrative);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from role_permission where role = 'SUBDIRECTOR' and permission_id = (select id from permission where code = 'document.debt-exception.issue')");
        permissionSnapshotCache.invalidate();
        jdbc.update("delete from document_debt_exception");
    }

    @Test
    void administrativeIssuesAnExceptionAndItIsAuditedWithoutTheReason() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.studentId").value(studentId));

        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        List<String> auditedReasons = jdbc.queryForList(
                "select coalesce(reason, '') || coalesce(before_value, '') || coalesce(after_value, '') from audit_log where action = 'DOCUMENT_DEBT_EXC_ISSUED' and entity_id = ?",
                String.class, id);
        assertThat(auditedReasons).hasSize(1);
        assertThat(auditedReasons.get(0)).doesNotContain(REASON).contains("GRADUATION_CERTIFICATE");
    }

    @Test
    void directorCanIssueAnException() throws Exception {
        issue(directorToken, studentId, "EXAM_CERTIFICATE", REASON, 90).andExpect(status().isCreated());
    }

    @Test
    void subdirectorIsDeniedIssuingAndListing() throws Exception {
        issue(subdirectorToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions").header("Authorization", "Bearer " + subdirectorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void subdirectorIsAllowedOnlyWhileTheDelegationExists() throws Exception {
        issue(subdirectorToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());

        jdbc.update("insert into role_permission (role, permission_id) select 'SUBDIRECTOR', id from permission where code = 'document.debt-exception.issue'");
        permissionSnapshotCache.invalidate();
        issue(subdirectorToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        mockMvc.perform(get("/document-debt-exceptions").header("Authorization", "Bearer " + subdirectorToken))
                .andExpect(status().isOk());

        jdbc.update("delete from role_permission where role = 'SUBDIRECTOR' and permission_id = (select id from permission where code = 'document.debt-exception.issue')");
        permissionSnapshotCache.invalidate();
        issue(subdirectorToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());
    }

    @Test
    void personWithBothSubdirectorAndAdministrativeRolesIsGovernedBySubdirectorOnly() throws Exception {
        String dualToken = transactions.execute(status -> jwtService.generateAccessToken(persistPersonWithRoles("SUBDIRECTOR", "ADMINISTRATIVO")));
        issue(dualToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());

        jdbc.update("insert into role_permission (role, permission_id) select 'SUBDIRECTOR', id from permission where code = 'document.debt-exception.issue'");
        permissionSnapshotCache.invalidate();
        issue(dualToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
    }

    @Test
    void studentCannotIssueListOrDownloadExceptions() throws Exception {
        issue(studentToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions/1/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reasonShorterThanTwentyRealCharactersIsRejected() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", "corto                    ", 5)
                .andExpect(status().isBadRequest());
    }

    @Test
    void validityOutsideOneToNinetyDaysIsRejected() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 0).andExpect(status().isBadRequest());
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 91).andExpect(status().isBadRequest());
    }

    @Test
    void unknownStudentIsRejected() throws Exception {
        issue(administrativeToken, 999999999L, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStudentAndStatus() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        issue(administrativeToken, otherStudentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        mockMvc.perform(get("/document-debt-exceptions").param("studentId", studentId.toString()).param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].studentId").value(studentId));
    }

    @Test
    void activeExceptionIsConsumedExactlyOnce() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        Optional<Long> first = consume(studentId, CertificateDocumentType.GRADUATION_CERTIFICATE, null);
        Optional<Long> second = consume(studentId, CertificateDocumentType.GRADUATION_CERTIFICATE, null);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, first.get()))
                .isEqualTo("USED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", first.get())).isEqualTo(1);
    }

    @Test
    void exceptionIsOnlyConsumableByItsStudentAndDocumentType() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        assertThat(consume(otherStudentId, CertificateDocumentType.GRADUATION_CERTIFICATE, null)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_CERTIFICATE, null)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.GRADUATION_CERTIFICATE, null)).isPresent();
    }

    @Test
    void exceptionBoundToARegistrationOnlyServesThatRegistration() throws Exception {
        issueForRegistration(studentId, "EXAM_REGISTRATION_CERTIFICATE", 77L).andExpect(status().isCreated());

        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, null)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, 78L)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, 77L)).isPresent();
    }

    @Test
    void expiredExceptionCannotBeConsumedAndExpiryIsAudited() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 1).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        assertThat(consume(studentId, CertificateDocumentType.GRADUATION_CERTIFICATE, null)).isEmpty();
        assertThat(expirer.expireDue()).isEqualTo(1);
        assertThat(expirer.expireDue()).isZero();
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, id)).isEqualTo("EXPIRED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_EXPIRED", id)).isEqualTo(1);
    }

    @Test
    void firstDownloadConsumesTheExceptionAndSecondIsRejected() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);

        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("pdf".getBytes()));
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, id)).isEqualTo("USED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", id)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", id)).isEqualTo(1);

        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEBT_EXCEPTION_ALREADY_USED"));
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", id)).isEqualTo(1);
    }

    @Test
    void downloadingAnExpiredExceptionIsRejected() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 1).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        actAs(administrative);

        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEBT_EXCEPTION_EXPIRED"));
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", id)).isZero();
    }

    @Test
    void downloadingAMissingExceptionIsNotFound() throws Exception {
        mockMvc.perform(get("/document-debt-exceptions/999999/pdf").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEBT_EXCEPTION_NOT_FOUND"));
    }

    @Test
    void typeWithoutIssuerIsUnprocessableAndTheExceptionStaysActive() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);

        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test
    void failedRenderRollsBackAndTheExceptionStaysActive() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        FAIL_RENDER.set(true);
        try {
            actAs(administrative);
            mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                    .andExpect(status().is5xxServerError());
        } finally {
            FAIL_RENDER.set(false);
        }
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", id)).isZero();
        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk());
    }

    @Test
    void issuerRejectsAScopeThatDoesNotBelongToTheStudent() throws Exception {
        mockMvc.perform(post("/document-debt-exceptions").header("Authorization", "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"documentType\":\"EXAM_CERTIFICATE\",\"registrationId\":404,"
                                + "\"reason\":\"" + REASON + "\",\"validityDays\":5}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from document_debt_exception", Integer.class)).isZero();
    }

    @Test
    void twoSimultaneousDownloadsGiveOneDocumentAndOneAlreadyUsed() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<MvcResult> download = () -> {
            ready.countDown();
            go.await();
            return mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                    .andReturn();
        };
        Future<MvcResult> first = executor.submit(download);
        Future<MvcResult> second = executor.submit(download);
        ready.await();
        go.countDown();
        List<MvcResult> results = List.of(first.get(), second.get());
        executor.shutdown();

        assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
        MvcResult loser = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat(loser.getResponse().getContentAsString()).contains("DEBT_EXCEPTION_ALREADY_USED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", id)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", id)).isEqualTo(1);
    }

    @Test
    void downloadAndIssuerGateRaceHasExactlyOneWinner() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> download = executor.submit(() -> {
            ready.countDown();
            go.await();
            return mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                    .andReturn().getResponse().getStatus();
        });
        Future<Boolean> gateConsumption = executor.submit(() -> {
            actAs(administrative);
            ready.countDown();
            go.await();
            try {
                return gate.consumeActiveException(studentId, CertificateDocumentType.EXAM_CERTIFICATE, null).isPresent();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        ready.await();
        go.countDown();
        int downloadStatus = download.get();
        boolean gateWon = gateConsumption.get();
        executor.shutdown();

        assertThat(downloadStatus == 200 ^ gateWon).isTrue();
        assertThat(downloadStatus).isIn(200, 409);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", id)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", id)).isEqualTo(downloadStatus == 200 ? 1 : 0);
    }

    @Test
    void issuerAuditDetailWithAFreeTextValueFailsTheDownloadAndRollsBack() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        AUDIT_DETAIL.set("reason=" + REASON);
        try {
            actAs(administrative);
            mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                    .andExpect(status().is4xxClientError());
        } finally {
            AUDIT_DETAIL.set("documentId=1");
        }
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", id)).isZero();
    }

    @Test
    void downloadMatrixAllowsOnlyDirectorAndAdministrative() throws Exception {
        Long directorExceptionId = issueAndGetId();
        Long administrativeExceptionId = issueAndGetId();
        Long deniedExceptionId = issueAndGetId();
        String teacherToken = tokenOf("DOCENTE");
        String preceptorToken = tokenOf("PRECEPTOR");

        assertPdfStatus(directorExceptionId, directorToken, 200);
        assertPdfStatus(administrativeExceptionId, administrativeToken, 200);
        assertPdfStatus(deniedExceptionId, subdirectorToken, 403);
        assertPdfStatus(deniedExceptionId, studentToken, 403);
        assertPdfStatus(deniedExceptionId, teacherToken, 403);
        assertPdfStatus(deniedExceptionId, preceptorToken, 403);
        mockMvc.perform(get("/document-debt-exceptions/" + deniedExceptionId + "/pdf")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, deniedExceptionId))
                .isEqualTo("ACTIVE");
    }

    private Long issueAndGetId() throws Exception {
        issue(administrativeToken, studentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        actAs(administrative);
        return jdbc.queryForObject("select max(id) from document_debt_exception", Long.class);
    }

    private String tokenOf(String roleName) {
        return transactions.execute(status -> jwtService.generateAccessToken(persistPerson(roleName)));
    }

    private void assertPdfStatus(Long id, String token, int expected) throws Exception {
        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }

    @Test
    void listingExpiresDueExceptionsBeforeFiltering() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 1).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        actAs(administrative);

        mockMvc.perform(get("/document-debt-exceptions").param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions").param("status", "EXPIRED")
                        .header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        assertThat(auditCount("DOCUMENT_DEBT_EXC_EXPIRED", id)).isEqualTo(1);
    }

    @Test
    void pdfEndpointHonoursThePermissionMatrix() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);

        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isUnprocessableEntity());
        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isUnprocessableEntity());
        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + subdirectorToken))
                .andExpect(status().isForbidden());
        actAs(administrative);
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions/" + id + "/pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anExceptionAtItsExpiryInstantCannotBeMarkedUsed() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 1).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 11, 10, 0);

        int atExpiry = transactions.execute(status -> repository.markUsedIfActive(id, expiresAt, administrative.getId()));
        int beforeExpiry = transactions.execute(status -> repository.markUsedIfActive(id, expiresAt.minusSeconds(1), administrative.getId()));

        assertThat(atExpiry).isZero();
        assertThat(beforeExpiry).isEqualTo(1);
    }

    @Test
    void exceptionWithoutRegistrationServesAnyRegistration() throws Exception {
        issue(administrativeToken, studentId, "EXAM_REGISTRATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, 99L)).isPresent();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, 99L)).isEmpty();
    }

    @Test
    void twoSimultaneousConsumptionsOnlyOneWins() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Optional<Long>> consumption = () -> {
            actAs(administrative);
            ready.countDown();
            go.await();
            try {
                return gate.consumeActiveException(studentId, CertificateDocumentType.GRADUATION_CERTIFICATE, null);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        List<Future<Optional<Long>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(consumption));
        }
        ready.await();
        go.countDown();
        int winners = 0;
        for (Future<Optional<Long>> future : futures) {
            winners += future.get().isPresent() ? 1 : 0;
        }
        executor.shutdown();

        assertThat(winners).isEqualTo(1);
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", id)).isEqualTo(1);
    }

    private Optional<Long> consume(Long student, CertificateDocumentType type, Long registrationId) {
        actAs(administrative);
        return gate.consumeActiveException(student, type, registrationId);
    }

    @Test
    void anAlreadyUsedExceptionCannotBeMarkedUsedAgain() throws Exception {
        issue(administrativeToken, studentId, "GRADUATION_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 10, 0);

        int first = transactions.execute(status -> repository.markUsedIfActive(id, now, administrative.getId()));
        int second = transactions.execute(status -> repository.markUsedIfActive(id, now, administrative.getId()));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    private int auditCount(String action, Long entityId) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, entityId);
    }

    private ResultActions issue(String token, Long student, String documentType, String reason, int validityDays) throws Exception {
        return mockMvc.perform(post("/document-debt-exceptions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student + ",\"documentType\":\"" + documentType + "\",\"reason\":\"" + reason
                        + "\",\"validityDays\":" + validityDays + "}"));
    }

    private ResultActions issueForRegistration(Long student, String documentType, Long registrationId) throws Exception {
        return mockMvc.perform(post("/document-debt-exceptions").header("Authorization", "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student + ",\"documentType\":\"" + documentType + "\",\"registrationId\":" + registrationId
                        + ",\"reason\":\"" + REASON + "\",\"validityDays\":5}"));
    }

    private void actAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Student persistStudent() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson("ALUMNO"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Person persistPerson(String roleName) {
        return persistPersonWithRoles(roleName);
    }

    private Person persistPersonWithRoles(String... roleNames) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("debtexc." + suffix);
        person.setEmail("debtexc." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        for (String roleName : roleNames) {
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        }
        entityManager.persist(person);
        return person;
    }
}
