package com.red_escuela.backend.graduation.certificate.signatory;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CertificateSignatoryUploadLimitHttpIT extends AbstractIntegrationTest {

    private static final String BOUNDARY = "----SignatoryUploadLimitBoundary";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @LocalServerPort
    int port;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    private String directorToken() {
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Limite");
            person.setLastname("Firma");
            person.setUsername("signatory.limit.director");
            person.setDni("93000001");
            person.setEmail("signatory.limit.director@example.com");
            person.setPassword("not-used");
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("DIRECTOR").orElseThrow());
            entityManager.persist(person);
            entityManager.flush();
            return jwtService.generateAccessToken(person);
        });
    }

    private Long signatoryId() {
        return jdbc.queryForObject("select id from certificate_signatory order by id limit 1", Long.class);
    }

    private HttpResponse<String> putSignature(Long id, byte[] content, String token) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 512);
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"firma.png\"\r\nContent-Type: image/png\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/certificate-signatories/" + id + "/signature"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void signatureOverTheMultipartLimitIsRejectedAsSignatureInvalidOverRealHttp() throws Exception {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        Arrays.fill(content, (byte) 'a');
        System.arraycopy(PNG_SIGNATURE, 0, content, 0, PNG_SIGNATURE.length);

        HttpResponse<String> response = putSignature(signatoryId(), content, directorToken());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("SIGNATORY_SIGNATURE_INVALID");
        assertThat(jdbc.queryForObject("select count(*) from certificate_signatory_signature", Integer.class)).isZero();
    }
}
