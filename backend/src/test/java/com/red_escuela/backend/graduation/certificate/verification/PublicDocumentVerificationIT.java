package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.verification.specialist.DocumentVerificationSource;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PublicDocumentVerificationIT extends AbstractIntegrationTest {

    private static final String SNAPSHOT = "{\"holderFullName\":\"PÉREZ, ANA\",\"documentTypeLabel\":\"DNI\","
            + "\"documentNumber\":\"30123456\",\"titleKind\":\"la carrera\",\"titleName\":\"Tecnicatura en Enfermería\","
            + "\"programLevel\":\"UNDERGRADUATE\",\"hours\":1860,\"graduationDate\":\"2026-07-15\",\"issuedOn\":\"2026-11-05\","
            + "\"institutionName\":\"Instituto\",\"signatories\":[],\"templateRef\":\"BUILT_IN_V1\"}";
    private static final Set<CertificateDocumentType> TYPES_WITHOUT_ISSUER_YET = EnumSet.of(
            CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE,
            CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE,
            CertificateDocumentType.EXAM_CERTIFICATE);
    private static final AtomicInteger CLIENTS = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private List<DocumentVerificationSource> sources;

    private final List<Long> graduationIds = new ArrayList<>();
    private final List<Long> regularIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        MutableClock.INSTANCE.reset();
        graduationIds.forEach(id -> {
            jdbc.update("delete from verifiable_document where document_type = 'GRADUATION_CERTIFICATE' and source_id = ?", id);
            jdbc.update("update graduation_certificate set replaces_id = null where id = ?", id);
        });
        graduationIds.forEach(id -> jdbc.update("delete from graduation_certificate where id = ?", id));
        regularIds.forEach(id -> {
            jdbc.update("delete from verifiable_document where document_type = 'REGULAR_STUDENT_CERTIFICATE' and source_id = ?", id);
            jdbc.update("delete from regular_student_certificate where id = ?", id);
        });
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

    private String insertGraduation(String status, Long replacesId) {
        return insertGraduation(status, replacesId, true);
    }

    private String insertGraduation(String status, Long replacesId, boolean registered) {
        String code = CertificateCode.generate(new SecureRandom()).value();
        long[] id = new long[1];
        transactions.executeWithoutResult(tx -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            id[0] = jdbc.queryForObject("insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, "
                    + "issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, registry_number, revoked_at, revoked_by_id, "
                    + "revocation_reason, replaces_id) values (?, 'PROGRAM', ?, 987654, ?, '2026-07-15', '2026-11-05 10:00:00', 1, ?::jsonb, "
                    + "'BUILT_IN_V1', repeat('a', 64), 'x.pdf', ?, ?, ?, ?, ?) returning id", Long.class, code,
                    ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000), status, SNAPSHOT,
                    String.format("%07d/2026", ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999)),
                    "REVOKED".equals(status) ? Timestamp.valueOf("2026-11-09 08:00:00") : null,
                    "REVOKED".equals(status) ? 1L : null, "REVOKED".equals(status) ? "motivo interno" : null, replacesId);
            if (registered) {
                jdbc.update("insert into verifiable_document (code, document_type, source_id, registered_at) values (?, 'GRADUATION_CERTIFICATE', ?, now())",
                        code, id[0]);
            }
        });
        graduationIds.add(id[0]);
        return code;
    }

    private Long graduationIdOf(String code) {
        return jdbc.queryForObject("select id from graduation_certificate where code = ?", Long.class, code);
    }

    private String insertRegular(String status, LocalDateTime expiresAt) {
        return insertRegular(status, expiresAt, true);
    }

    private String insertRegular(String status, LocalDateTime expiresAt, boolean registered) {
        String code = CertificateCode.generate(new SecureRandom()).value();
        long[] id = new long[1];
        transactions.executeWithoutResult(tx -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            id[0] = jdbc.queryForObject("insert into regular_student_certificate (code, kind, program_enrollment_id, student_id, status, issued_at, "
                    + "expires_at, validity_days, issued_by_id, issue_channel, request_id, billing_mode_at_issue, standing_basis, "
                    + "standing_confirmed_by_id, standing_confirmed_at, standing_evidence, snapshot, template_ref, pdf_sha256, pdf_path, "
                    + "revoked_at, revocation_reason) values (?, 'PROGRAM', ?, 987654, ?, '2026-11-05 10:00:00', ?, 30, 1, 'STAFF', "
                    + "555, 'OFF', 'MANUALLY_CONFIRMED', 1, '2026-11-05 10:00:00', 'recibo 1234 verificado', ?::jsonb, "
                    + "'REGULAR_BUILT_IN_V1', repeat('a', 64), 'x.pdf', ?, ?) returning id", Long.class, code,
                    ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000), status, Timestamp.valueOf(expiresAt), SNAPSHOT,
                    "REVOKED".equals(status) ? Timestamp.valueOf("2026-11-09 08:00:00") : null,
                    "REVOKED".equals(status) ? "motivo interno" : null);
            if (registered) {
                jdbc.update("insert into verifiable_document (code, document_type, source_id, registered_at) values (?, 'REGULAR_STUDENT_CERTIFICATE', ?, now())",
                        code, id[0]);
            }
        });
        regularIds.add(id[0]);
        return code;
    }

    private static String withoutRequestId(String body) {
        return body.replaceAll("\"requestId\":\"[^\"]*\"", "");
    }

    private MvcResult verify(String code) throws Exception {
        return mockMvc.perform(get("/public/documents/{code}", code).with(from(freshClient()))).andReturn();
    }

    @Test
    void everyDocumentTypeHasAVerificationSourceOrIsExplicitlyPending() {
        Set<CertificateDocumentType> covered = sources.stream().map(DocumentVerificationSource::type).collect(Collectors.toSet());

        Set<CertificateDocumentType> uncovered = EnumSet.allOf(CertificateDocumentType.class);
        uncovered.removeAll(covered);
        assertThat(uncovered).isEqualTo(TYPES_WITHOUT_ISSUER_YET);
    }

    @Test
    void validGraduationCertificateResolvesWithMinimalData() throws Exception {
        String code = insertGraduation("VALID", null);

        MvcResult result = verify(code);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"documentType\":\"GRADUATION_CERTIFICATE\"", "\"status\":\"VALID\"", "\"maskedDocument\":\"*****456\"",
                "/2026\"}", "PÉREZ, ANA");
        assertThat(body).doesNotContain("Enfermería", "30123456", "hours", "grade", "result", "origin", "motivo interno", "titleName");
    }

    @Test
    void revokedGraduationCertificateReportsRevokedWithoutReason() throws Exception {
        String code = insertGraduation("REVOKED", null);

        MvcResult result = verify(code);

        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"REVOKED\"", "\"revokedAt\":\"2026-11-09T08:00:00\"")
                .doesNotContain("motivo interno");
    }

    @Test
    void revokedGraduationCertificateWithAReplacementReportsReplaced() throws Exception {
        String original = insertGraduation("REVOKED", null);
        insertGraduation("VALID", graduationIdOf(original));

        MvcResult result = verify(original);

        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"REPLACED\"", "\"replacedAt\":\"2026-11-05T10:00:00\"")
                .doesNotContain("\"revokedAt\":\"2026");
    }

    @Test
    void regularCertificateIsValidThenExpiredWithAFixedClock() throws Exception {
        String code = insertRegular("VALID", LocalDateTime.of(2026, 12, 5, 10, 0));

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 5, 9, 59));
        assertThat(verify(code).getResponse().getContentAsString()).contains("\"documentType\":\"REGULAR_STUDENT_CERTIFICATE\"", "\"status\":\"VALID\"")
                .contains("\"expiresAt\":\"2026-12-05T10:00:00\"");

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 5, 10, 1));
        assertThat(verify(code).getResponse().getContentAsString()).contains("\"status\":\"EXPIRED\"");
    }

    @Test
    void revokedRegularCertificateReportsRevokedWithoutReason() throws Exception {
        String code = insertRegular("REVOKED", LocalDateTime.of(2027, 1, 5, 10, 0));

        assertThat(verify(code).getResponse().getContentAsString()).contains("\"status\":\"REVOKED\"")
                .doesNotContain("motivo interno", "recibo 1234", "Enfermería");
    }

    @Test
    void unknownMalformedAndOversizedCodesAnswerTheSameGeneric404() throws Exception {
        String unknown = CertificateCode.generate(new SecureRandom()).value();

        String unknownBody = verify(unknown).getResponse().getContentAsString();
        String malformedBody = verify("no-es-un-codigo").getResponse().getContentAsString();
        String oversizedBody = verify("A".repeat(200)).getResponse().getContentAsString();

        mockMvc.perform(get("/public/documents/{code}", unknown).with(from(freshClient()))).andExpect(status().isNotFound());
        assertThat(withoutRequestId(malformedBody)).isEqualTo(withoutRequestId(unknownBody));
        assertThat(withoutRequestId(oversizedBody)).isEqualTo(withoutRequestId(unknownBody));
    }

    @Test
    void registeredDocumentOfATypeWithoutSourceAnswersTheSameGeneric404() throws Exception {
        String code = CertificateCode.generate(new SecureRandom()).value();
        jdbc.update("insert into verifiable_document (code, document_type, source_id, registered_at) values (?, 'EXAM_CERTIFICATE', 1, now())", code);
        try {
            MvcResult withoutSource = verify(code);
            MvcResult unknown = verify(CertificateCode.generate(new SecureRandom()).value());

            assertThat(withoutSource.getResponse().getStatus()).isEqualTo(404);
            assertThat(withoutRequestId(withoutSource.getResponse().getContentAsString()))
                    .isEqualTo(withoutRequestId(unknown.getResponse().getContentAsString()));
        } finally {
            jdbc.update("delete from verifiable_document where code = ?", code);
        }
    }

    @Test
    void regularCertificateIsStillValidAtTheExactInstantOfExpiry() throws Exception {
        String code = insertRegular("VALID", LocalDateTime.of(2026, 12, 5, 10, 0));

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 5, 10, 0));

        assertThat(verify(code).getResponse().getContentAsString()).contains("\"status\":\"VALID\"");
    }

    @Test
    void repeatedMissesFromOneClientAreRateLimited() throws Exception {
        String client = freshClient();
        String unknown = CertificateCode.generate(new SecureRandom()).value();
        int limited = 0;

        for (int attempt = 0; attempt < 40 && limited == 0; attempt++) {
            int httpStatus = mockMvc.perform(get("/public/documents/{code}", unknown).with(from(client))).andReturn().getResponse().getStatus();
            if (httpStatus == 429) {
                limited++;
            }
        }

        assertThat(limited).isEqualTo(1);
    }

    @Test
    void publicConsultationsAreNotAuditedOneByOne() throws Exception {
        String code = insertGraduation("VALID", null);
        Integer before = jdbc.queryForObject("select count(*) from audit_log", Integer.class);

        verify(code);
        verify(CertificateCode.generate(new SecureRandom()).value());

        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(before);
    }

    @Test
    void migrationBackfillRegistersAlreadyIssuedDocumentsThatWereNotRegistered() throws Exception {
        String graduation = insertGraduation("VALID", null, false);
        String regular = insertRegular("VALID", LocalDateTime.of(2027, 1, 5, 10, 0), false);
        assertThat(verify(graduation).getResponse().getStatus()).isEqualTo(404);
        assertThat(verify(regular).getResponse().getStatus()).isEqualTo(404);

        List<String> backfills = backfillStatements();
        assertThat(backfills).hasSize(2);
        backfills.forEach(statement -> {
            jdbc.update(statement.replace("from graduation_certificate", "from graduation_certificate where code = '" + graduation + "'")
                    .replace("from regular_student_certificate", "from regular_student_certificate where code = '" + regular + "'"));
        });

        assertThat(verify(graduation).getResponse().getStatus()).isEqualTo(200);
        assertThat(verify(regular).getResponse().getStatus()).isEqualTo(200);
    }

    private static List<String> backfillStatements() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__verifiable_document_registry.sql");
        assertThat(files).hasSize(1);
        String sql = new String(files[0].getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(statement -> statement.startsWith("insert into verifiable_document"))
                .toList();
    }

    @Test
    void responsesAreNeverCachedForFoundAndNotFound() throws Exception {
        String code = insertGraduation("VALID", null);

        mockMvc.perform(get("/public/documents/{code}", code).with(from(freshClient())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(get("/public/documents/{code}", CertificateCode.generate(new SecureRandom()).value()).with(from(freshClient())))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void lowercaseHyphenatedAndSpacedCodesResolveTheSameDocument() throws Exception {
        String code = insertGraduation("VALID", null);
        String formatted = new CertificateCode(code).formatted().toLowerCase(Locale.ROOT);

        assertThat(verify(formatted).getResponse().getStatus()).isEqualTo(200);
        assertThat(verify(formatted.replace("-", " ")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unicodeLookalikeCodesAreNotFound() throws Exception {
        String code = insertGraduation("VALID", null);
        String fullWidth = "\uFF21" + code.substring(1);
        String turkishDotted = code.replace('I', '\u0130').replace('K', '\u212A') + "\u0130";

        assertThat(verify(fullWidth).getResponse().getStatus()).isEqualTo(404);
        assertThat(verify(turkishDotted).getResponse().getStatus()).isEqualTo(404);
        assertThat(verify(code.substring(0, 25) + "\u00D1").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void rawCodeLengthLimitIsExactlySixtyFour() throws Exception {
        String code = insertGraduation("VALID", null);
        String atLimit = code + "-".repeat(38);
        String overLimit = code + "-".repeat(39);

        assertThat(atLimit).hasSize(64);
        assertThat(verify(atLimit).getResponse().getStatus()).isEqualTo(200);
        assertThat(verify(overLimit).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void legacyCertificatesEndpointKeepsResolvingIssuedCodes() throws Exception {
        String code = insertGraduation("VALID", null);

        mockMvc.perform(get("/public/certificates/{code}", code).with(from(freshClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentKind").value("GRADUATION"));
    }
}
