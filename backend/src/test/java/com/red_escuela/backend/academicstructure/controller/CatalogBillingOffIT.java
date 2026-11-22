package com.red_escuela.backend.academicstructure.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.service.ProgramService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class CatalogBillingOffIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProgramService programService;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;

    private Long programId;
    private String studentToken;

    @BeforeEach
    void createPricedProgramAndStudent() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 1));
        studentToken = transactions.execute(status -> {
            Person staff = new CycleTestFixtures(entityManager).person();
            entityManager.flush();
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
            return jwtService.generateAccessToken(persistStudentPerson());
        });
        Program program = programService.create(new CreateProgramRequest(
                "Carrera " + UUID.randomUUID(), 3, ars("1000"), ars("500"), 100, null, null, null, null, null, null));
        programId = program.getId();
    }

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void billingOffRemovesEveryProgramPriceFromTheCatalogResponses() throws Exception {
        switchBillingTo("OFF", false);

        mockMvc.perform(get("/programs/" + programId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.enrollmentFeeAmount").doesNotExist());
        mockMvc.perform(get("/programs?status=ACTIVE").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + programId + ")].price").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.id==" + programId + ")].enrollmentFeeAmount").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(programId))
                .andExpect(jsonPath("$.matriculaCohorte1").doesNotExist())
                .andExpect(jsonPath("$.matriculaCohorte2").doesNotExist())
                .andExpect(jsonPath("$.cuotaMensual").doesNotExist());
    }

    @Test
    void billingFullKeepsProgramPricesInTheCatalogResponses() throws Exception {
        mockMvc.perform(get("/programs/" + programId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(1000.00))
                .andExpect(jsonPath("$.enrollmentFeeAmount.amount").value(500.00));
        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matriculaCohorte1.amount").value(500.00))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(1000.00));
    }

    @Test
    void billingMirrorKeepsProgramPricesInTheCatalogResponses() throws Exception {
        switchBillingTo("MIRROR", true);

        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotaMensual.amount").value(1000.00));
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
    }

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private Person persistStudentPerson() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername("alumno." + suffix);
        person.setEmail("alumno." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
