package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class BackupStatusControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Value("${app.backup.status-file}")
    private String statusFile;

    private Map<String, String> tokensByRole;

    @BeforeEach
    void createOneUserPerRole() throws IOException {
        Files.deleteIfExists(Path.of(statusFile));
        tokensByRole = transactions.execute(status -> {
            Map<String, String> tokens = new LinkedHashMap<>();
            for (String role : new String[] {"ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN"}) {
                tokens.put(role, jwtService.generateAccessToken(persistPerson(role)));
            }
            return tokens;
        });
    }

    @AfterEach
    void removeStatusFile() throws IOException {
        Files.deleteIfExists(Path.of(statusFile));
    }

    private void writeStatusFile(Instant generatedAt) throws IOException {
        Path file = Path.of(statusFile);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {
                  "schemaVersion": 1,
                  "generatedAt": "%s",
                  "targetRpoHours": 4,
                  "destination": {"path": "/var/backups/agustina", "freeBytes": 100, "totalBytes": 500},
                  "tiers": [
                    {"tier": "DAILY", "takenAt": "%s", "label": null, "dumpSizeBytes": 10, "filesSizeBytes": 20,
                     "verification": "VERIFIED", "kept": 7, "fileNames": ["daily.dump.age", "daily-files.tar.age"]}
                  ],
                  "lastRestoreTest": {"ranAt": "%s", "result": "PASSED", "failureCode": null},
                  "alerts": [{"at": "%s", "level": "WARNING", "code": "DISK_LOW"}],
                  "consecutiveFailures": 0,
                  "lastCompleteBackupAt": "%s"
                }
                """.formatted(generatedAt, generatedAt, generatedAt, generatedAt, generatedAt), StandardCharsets.UTF_8);
    }

    private String bearer(String role) {
        return "Bearer " + tokensByRole.get(role);
    }

    @Test
    void directorDoesNotReceiveTheTechnicalFields() throws Exception {
        writeStatusFile(Instant.now().minus(Duration.ofHours(1)));

        mockMvc.perform(get("/backups/status").header("Authorization", bearer("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.disk.freeBytes").value(100))
                .andExpect(jsonPath("$.disk.path").value(nullValue()))
                .andExpect(jsonPath("$.tiers[0].tier").value("DAILY"))
                .andExpect(jsonPath("$.tiers[0].fileNames").value(nullValue()))
                .andExpect(jsonPath("$.alerts[0].code").value("DISK_LOW"));
    }

    @Test
    void platformAdminReceivesTheDiskPathAndTheFileNames() throws Exception {
        writeStatusFile(Instant.now().minus(Duration.ofHours(1)));

        mockMvc.perform(get("/backups/status").header("Authorization", bearer("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.disk.path").value("/var/backups/agustina"))
                .andExpect(jsonPath("$.tiers[0].fileNames[0]").value("daily.dump.age"))
                .andExpect(jsonPath("$.summary.targetRpoHours").value(4));
    }

    @Test
    void oldStatusFileIsReportedAsStale() throws Exception {
        writeStatusFile(Instant.now().minus(Duration.ofHours(9)));

        mockMvc.perform(get("/backups/status").header("Authorization", bearer("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STALE"));
    }

    @Test
    void missingStatusFileIsNotConfiguredAndNeverAnError() throws Exception {
        mockMvc.perform(get("/backups/status").header("Authorization", bearer("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.generatedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tiers").isEmpty());
    }

    @Test
    void corruptStatusFileIsNotConfiguredAndNeverAnError() throws Exception {
        Path file = Path.of(statusFile);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ corrupto", StandardCharsets.UTF_8);

        mockMvc.perform(get("/backups/status").header("Authorization", bearer("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"));
    }

    @Test
    void otherRolesAreForbiddenAndAnonymousIsUnauthorized() throws Exception {
        writeStatusFile(Instant.now());

        for (String role : new String[] {"ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO"}) {
            mockMvc.perform(get("/backups/status").header("Authorization", bearer(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/backups/status")).andExpect(status().isUnauthorized());
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername(roleName.toLowerCase() + "." + suffix);
        person.setEmail(roleName.toLowerCase() + "." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
