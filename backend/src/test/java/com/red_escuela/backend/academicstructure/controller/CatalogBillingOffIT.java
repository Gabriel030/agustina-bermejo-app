package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.testsupport.PreceptorTestFactory;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.service.CourseService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class CatalogBillingOffIT extends AbstractIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private PreceptorTestFactory preceptors;


    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProgramService programService;
    @Autowired
    private CourseService courseService;
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
    @Autowired
    private ObjectMapper objectMapper;

    private Long programId;
    private Long courseId;
    private Long termId;
    private String studentToken;
    private String adminToken;

    @BeforeEach
    void createPricedOfferingsAndUsers() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 1));
        transactions.executeWithoutResult(status -> {
            Person staff = new CycleTestFixtures(entityManager).person();
            termId = new CycleTestFixtures(entityManager).term(2027).getId();
            entityManager.flush();
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
            studentToken = jwtService.generateAccessToken(persistPerson("ALUMNO"));
            adminToken = jwtService.generateAccessToken(persistPerson("ADMINISTRATIVO"));
        });
        programId = programService.create(new CreateProgramRequest(
                "Carrera " + UUID.randomUUID(), 3, ars("1000"), ars("500"), 100, null, null, null, null, null, null)).getId();
        courseId = courseService.create(new CreateCourseRequest(
                "Curso " + UUID.randomUUID(), termId, null, 10, ars("800"), 6, null, null, null, null, null, java.util.List.of(preceptors.createPreceptorId()))).getId();
    }

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void billingOffRemovesEveryProgramPriceForStudents() throws Exception {
        switchBillingTo("OFF", false);

        mockMvc.perform(get("/programs/" + programId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.enrollmentFeeAmount").doesNotExist());
        mockMvc.perform(get("/programs?status=ACTIVE").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + programId + ")].price").value(everyItem(nullValue())))
                .andExpect(jsonPath("$[?(@.id==" + programId + ")].enrollmentFeeAmount").value(everyItem(nullValue())));
        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(programId))
                .andExpect(jsonPath("$.matriculaCohorte1").doesNotExist())
                .andExpect(jsonPath("$.matriculaCohorte2").doesNotExist())
                .andExpect(jsonPath("$.cuotaMensual").doesNotExist());
    }

    @Test
    void billingOffRemovesEveryCoursePriceForStudents() throws Exception {
        switchBillingTo("OFF", false);

        mockMvc.perform(get("/courses/" + courseId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").doesNotExist());
        mockMvc.perform(get("/courses?status=ACTIVE").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + courseId + ")].price").value(everyItem(nullValue())));
        mockMvc.perform(get("/courses/" + courseId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.cuotaMensual").doesNotExist());
    }

    @Test
    void billingFullKeepsPricesForStudents() throws Exception {
        mockMvc.perform(get("/programs/" + programId).header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.price.amount").value(1000.00))
                .andExpect(jsonPath("$.enrollmentFeeAmount.amount").value(500.00));
        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.matriculaCohorte1.amount").value(500.00))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(1000.00));
        mockMvc.perform(get("/courses/" + courseId).header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.price.amount").value(800.00));
        mockMvc.perform(get("/courses/" + courseId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(800.00));
    }

    @Test
    void billingMirrorKeepsPricesForStudents() throws Exception {
        switchBillingTo("MIRROR", true);

        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(1000.00));
        mockMvc.perform(get("/courses/" + courseId).header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.price.amount").value(800.00));
    }

    @Test
    void billingOffKeepsPricesForStaffOnReads() throws Exception {
        switchBillingTo("OFF", false);

        mockMvc.perform(get("/programs/" + programId).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.price.amount").value(1000.00));
        mockMvc.perform(get("/programs/" + programId + "/pricing").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(1000.00));
        mockMvc.perform(get("/courses?status=ACTIVE").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$[?(@.id==" + courseId + " && @.price != null)]").isNotEmpty());
        mockMvc.perform(get("/courses/" + courseId + "/pricing").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(800.00));
    }

    @Test
    void billingOffKeepsPricesForStaffOnWrites() throws Exception {
        switchBillingTo("OFF", false);
        var createProgram = new CreateProgramRequest("Carrera nueva " + UUID.randomUUID(), 3, ars("1100"), ars("600"), 100, null, null, null, null, null, null);
        var updateProgram = new UpdateProgramRequest();
        updateProgram.setPrice(ars("1300"));
        var createCourse = new CreateCourseRequest("Curso nuevo " + UUID.randomUUID(), termId, null, 10, ars("900"), 6, null, null, null, null, null, java.util.List.of(preceptors.createPreceptorId()));
        var updateCourse = new UpdateCourseRequest(null, null, null, ars("950"), null, null, null, null);

        mockMvc.perform(post("/programs").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createProgram)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price.amount").value(1100.00));
        mockMvc.perform(put("/programs/" + programId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateProgram)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(1300.00));
        mockMvc.perform(post("/courses").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createCourse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price.amount").value(900.00));
        mockMvc.perform(put("/courses/" + courseId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateCourse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(950.00));
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
    }

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
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
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
