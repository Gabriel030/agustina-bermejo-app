package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PublicCertificateControllerIT extends AbstractIntegrationTest {

    private static final String SNAPSHOT = "{\"holderFullName\":\"PÉREZ, ANA\",\"documentTypeLabel\":\"DNI\","
            + "\"documentNumber\":\"30123456\",\"titleKind\":\"la carrera\",\"titleName\":\"Tecnicatura en Enfermería\","
            + "\"programLevel\":\"UNDERGRADUATE\",\"hours\":1860,\"graduationDate\":\"2026-07-15\",\"issuedOn\":\"2026-11-05\","
            + "\"institutionName\":\"Instituto\",\"signatories\":[],\"templateRef\":\"BUILT_IN_V1\"}";
    private static final AtomicInteger CLIENTS = new AtomicInteger(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private final List<String> graduationCodes = new ArrayList<>();
    private final List<String> regularCodes = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        MutableClock.INSTANCE.reset();
        graduationCodes.forEach(code -> jdbc.update("delete from graduation_certificate where code = ?", code));
        regularCodes.forEach(code -> jdbc.update("delete from regular_student_certificate where code = ?", code));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String freshClient() {
        return "198.51.100." + CLIENTS.incrementAndGet();
    }

    private String insertGraduation(String status, String registryNumber) {
        String code = CertificateCode.generate(new SecureRandom()).value();
        graduationCodes.add(code);
        transactions.executeWithoutResult(tx -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, "
                    + "issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, registry_number, revoked_at) "
                    + "values (?, 'PROGRAM', ?, 987654, ?, '2026-07-15', '2026-11-05 10:00:00', 1, ?::jsonb, 'BUILT_IN_V1', "
                    + "repeat('a', 64), 'x.pdf', ?, ?)", code, ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000), status,
                    SNAPSHOT, registryNumber, "REVOKED".equals(status) ? java.sql.Timestamp.valueOf("2026-11-09 08:00:00") : null);
        });
        return code;
    }

    private String insertRegular(String status, LocalDateTime expiresAt) {
        String code = CertificateCode.generate(new SecureRandom()).value();
        regularCodes.add(code);
        transactions.executeWithoutResult(tx -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("insert into regular_student_certificate (code, kind, program_enrollment_id, student_id, status, issued_at, "
                    + "expires_at, validity_days, issued_by_id, issue_channel, request_id, billing_mode_at_issue, standing_basis, "
                    + "standing_confirmed_by_id, standing_confirmed_at, standing_evidence, snapshot, template_ref, pdf_sha256, pdf_path, "
                    + "revoked_at, revocation_reason) values (?, 'PROGRAM', ?, 987654, ?, '2026-11-05 10:00:00', ?, 30, 1, 'STAFF', "
                    + "555, 'OFF', 'MANUALLY_CONFIRMED', 1, '2026-11-05 10:00:00', 'recibo 1234 verificado', ?::jsonb, "
                    + "'REGULAR_BUILT_IN_V1', repeat('a', 64), 'x.pdf', ?, ?)", code,
                    ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000), status, java.sql.Timestamp.valueOf(expiresAt), SNAPSHOT,
                    "REVOKED".equals(status) ? java.sql.Timestamp.valueOf("2026-11-09 08:00:00") : null,
                    "REVOKED".equals(status) ? "error de carga" : null);
        });
        return code;
    }

    @Test
    void validGraduationCertificateIsServedWithoutAuthentication() throws Exception {
        String code = insertGraduation("VALID", "1284");

        mockMvc.perform(get("/public/certificates/{code}", code).with(from(freshClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentKind").value("GRADUATION"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.holderFullName").value("PÉREZ, ANA"))
                .andExpect(jsonPath("$.maskedDocument").value("*****456"))
                .andExpect(jsonPath("$.titleName").value("Tecnicatura en Enfermería"))
                .andExpect(jsonPath("$.graduationDate").value("2026-07-15"))
                .andExpect(jsonPath("$.registryNumber").value("1284"))
                .andExpect(jsonPath("$.hours").value(1860));
    }

    @Test
    void revokedGraduationCertificateReturnsRevokedWithoutReason() throws Exception {
        String code = insertGraduation("REVOKED", null);

        MvcResult result = mockMvc.perform(get("/public/certificates/{code}", code).with(from(freshClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").value("2026-11-09T08:00:00"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("revocationReason");
    }

    @Test
    void regularCertificateIsValidBeforeAndExpiredAfterItsExpiryWithAFixedClock() throws Exception {
        String code = insertRegular("VALID", LocalDateTime.of(2026, 12, 5, 10, 0));
        String client = freshClient();

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 5, 9, 59));
        mockMvc.perform(get("/public/certificates/{code}", code).with(from(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentKind").value("REGULAR"))
                .andExpect(jsonPath("$.status").value("VALID"));

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 5, 10, 1));
        mockMvc.perform(get("/public/certificates/{code}", code).with(from(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expiresAt").value("2026-12-05T10:00:00"));
    }

    @Test
    void revokedRegularCertificateReturnsRevokedAndNeverExposesEvidenceOrReason() throws Exception {
        String code = insertRegular("REVOKED", LocalDateTime.of(2030, 1, 1, 0, 0));

        MvcResult result = mockMvc.perform(get("/public/certificates/{code}", code).with(from(freshClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("recibo").doesNotContain("error de carga").doesNotContain("standing")
                .doesNotContain("revocationReason").doesNotContain("issuedBy");
    }

    @Test
    void responseNeverContainsFullDocumentNumberRecordFileOrContactData() throws Exception {
        String graduation = insertGraduation("VALID", "1284");
        String regular = insertRegular("VALID", LocalDateTime.of(2030, 1, 1, 0, 0));

        for (String code : new String[] {graduation, regular}) {
            String body = mockMvc.perform(get("/public/certificates/{code}", code).with(from(freshClient())))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("30123456").contains("*****456").doesNotContainIgnoringCase("legajo")
                    .doesNotContainIgnoringCase("email").doesNotContainIgnoringCase("mail").doesNotContainIgnoringCase("amount")
                    .doesNotContainIgnoringCase("studentId").doesNotContain("987654").doesNotContain("\"id\"");
        }
    }

    @Test
    void responseIsNotCacheableForHitsAndMisses() throws Exception {
        String code = insertGraduation("VALID", null);
        String client = freshClient();

        mockMvc.perform(get("/public/certificates/{code}", code).with(from(client)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/public/certificates/{code}", CertificateCode.generate(new SecureRandom()).value()).with(from(client)))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownMalformedAndWrongLengthCodesReturnIdenticalResponses() throws Exception {
        String client = freshClient();
        List<String> bodies = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();

        for (String code : new String[] {CertificateCode.generate(new SecureRandom()).value(), "NOT-A-CODE", "7QK3M9XH2VBN4T8R",
                "1284", "0".repeat(200)}) {
            MvcResult result = mockMvc.perform(get("/public/certificates/{code}", code).with(from(client))).andReturn();
            statuses.add(result.getResponse().getStatus());
            bodies.add(result.getResponse().getContentAsString().replaceAll("\"requestId\":\"[^\"]*\",?", ""));
        }

        assertThat(statuses).containsOnly(404);
        assertThat(bodies).containsOnly(bodies.get(0));
        assertThat(bodies.get(0)).contains("CERTIFICATE_NOT_FOUND");
    }

    @Test
    void sequentialRegistryNumberAndRowIdDoNotResolveThroughThePublicRoute() throws Exception {
        insertGraduation("VALID", "1284");
        Long rowId = jdbc.queryForObject("select max(id) from graduation_certificate", Long.class);
        String client = freshClient();

        for (String candidate : new String[] {"1284", String.valueOf(rowId), "N.º 1.284"}) {
            mockMvc.perform(get("/public/certificates/{code}", candidate).with(from(client)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CERTIFICATE_NOT_FOUND"));
        }
    }

    @Test
    void lowercaseHyphenatedCodeIsAccepted() throws Exception {
        String code = insertGraduation("VALID", null);
        String formatted = new CertificateCode(code).formatted().toLowerCase();

        mockMvc.perform(get("/public/certificates/{code}", formatted).with(from(freshClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void rateLimitedIpGets429WithRetryAfter() throws Exception {
        String code = insertGraduation("VALID", null);
        String client = freshClient();

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/public/certificates/{code}", code).with(from(client))).andExpect(status().isOk());
        }

        MvcResult limited = mockMvc.perform(get("/public/certificates/{code}", code).with(from(client)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("REQUEST_RATE_LIMIT_EXCEEDED"))
                .andReturn();
        assertThat(Long.parseLong(limited.getResponse().getHeader("Retry-After"))).isPositive();
        mockMvc.perform(get("/public/certificates/{code}", code).with(from(freshClient()))).andExpect(status().isOk());
    }

    @Test
    void guessingAttemptsExhaustTheBudgetThreeTimesFasterThanLegitimateChecks() throws Exception {
        String client = freshClient();

        for (int i = 0; i < 7; i++) {
            mockMvc.perform(get("/public/certificates/{code}", CertificateCode.generate(new SecureRandom()).value()).with(from(client)))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(get("/public/certificates/{code}", CertificateCode.generate(new SecureRandom()).value()).with(from(client)))
                .andExpect(status().isTooManyRequests());
    }
}
