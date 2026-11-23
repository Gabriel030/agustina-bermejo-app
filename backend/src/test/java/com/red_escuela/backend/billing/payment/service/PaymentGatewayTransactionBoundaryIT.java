package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.dto.UpdatePaymentGatewayConfigRequest;
import com.red_escuela.backend.billing.operation.PaymentGatewayConfigChangeRecordingPort;
import com.red_escuela.backend.billing.usecase.paymentgateway.UpdatePaymentGatewayConfigUseCase;
import com.red_escuela.backend.billing.usecase.paymentgateway.UploadQrImageUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class PaymentGatewayTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

    @Autowired
    private UpdatePaymentGatewayConfigUseCase updateConfig;

    @Autowired
    private UploadQrImageUseCase uploadQrImage;

    @Autowired
    private PaymentGatewayConfigChangeRecordingPort changeRecordingPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.uploads.directory}")
    private String uploadsDirectory;

    private Person actor;

    @BeforeEach
    void seedActor() {
        actor = new TransactionTemplate(transactionManager).execute(status -> {
            String dni = "PG" + System.nanoTime();
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    @Test
    void configUpdateFailingWhileRecordingTheChangeLeavesTheConfigUntouched() {
        Map<String, Object> configBefore = firstConfigRow();
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate,
                "payment_gateway_config", "payment_gateway_config_change");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                steps.run(() -> { });
                return null;
            }).when(changeRecordingPort).recordChangeIfDifferent(any(), any(), any(), any());
            updateConfig.update(new UpdatePaymentGatewayConfigRequest("nuevo.alias", "1234567890123456789012", "Titular Nuevo", true), actor);
        });

        assertThat(firstConfigRow()).isEqualTo(configBefore);
    }

    @Test
    void configUpdateWithoutFailuresPersistsTheConfigAndTheChangeRecord() {
        long changesBefore = count("payment_gateway_config_change");

        updateConfig.update(new UpdatePaymentGatewayConfigRequest("alias.ok", "1234567890123456789012", "Titular Ok", true), actor);

        Map<String, Object> config = firstConfigRow();
        assertThat(config.get("bank_alias")).isEqualTo("alias.ok");
        assertThat(config.get("bank_cbu")).isEqualTo("1234567890123456789012");
        assertThat(count("payment_gateway_config_change")).isEqualTo(changesBefore + 1);
    }

    @Test
    void qrUploadFailingWhileRecordingTheChangeLeavesNoOrphanFileAndTheConfigUntouched() throws IOException {
        Map<String, Object> configBefore = firstConfigRow();
        List<String> filesBefore = storedQrFiles();
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate,
                "payment_gateway_config", "payment_gateway_config_change");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                steps.run(() -> { });
                return null;
            }).when(changeRecordingPort).recordChangeIfDifferent(any(), any(), any(), any());
            uploadQrImage.upload(new MockMultipartFile("qr", "qr.png", "image/png", PNG), actor);
        });

        assertThat(firstConfigRow()).isEqualTo(configBefore);
        assertThat(storedQrFiles()).isEqualTo(filesBefore);
    }

    @Test
    void qrUploadWithoutFailuresStoresTheFileAndPointsTheConfigAtIt() throws IOException {
        long changesBefore = count("payment_gateway_config_change");

        uploadQrImage.upload(new MockMultipartFile("qr", "qr.png", "image/png", PNG), actor);

        String path = (String) firstConfigRow().get("qr_image_path");
        assertThat(path).isNotNull();
        assertThat(Path.of(uploadsDirectory).resolve(path)).exists();
        assertThat(storedQrFiles()).contains(Path.of(path).getFileName().toString());
        assertThat(count("payment_gateway_config_change")).isEqualTo(changesBefore + 1);
    }

    private Map<String, Object> firstConfigRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from payment_gateway_config order by id limit 1");
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private List<String> storedQrFiles() throws IOException {
        Path directory = Path.of(uploadsDirectory).resolve("payment-gateway");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }
}
