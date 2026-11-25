package com.red_escuela.backend.enrollment.controller;

import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ProspectByStaffIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;

    private Long institutionId;
    private Person director;
    private String directorToken;

    @BeforeEach
    void createInstitutionAndDirector() {
        transactions.executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            institutionId = institution.getId();
            director = persistPerson("DIRECTOR");
            directorToken = jwtService.generateAccessToken(director);
        });
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Person person = new Person();
        person.setUsername(roleName.toLowerCase() + suffix);
        person.setDni(Long.toString(Math.abs((long) suffix.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(roleName.toLowerCase() + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        return person;
    }

    private String token(String roleName) {
        return transactions.execute(status -> jwtService.generateAccessToken(persistPerson(roleName)));
    }

    private String createBody(String email) throws Exception {
        return objectMapper.writeValueAsString(new CreateProspectRequest("Ana", "Paz", email, "1155550000", institutionId));
    }

    private Long createByStaff(String email) throws Exception {
        return createByStaff("Ana", "Paz", email);
    }

    private Long createByStaff(String firstName, String lastName, String email) throws Exception {
        String response = mockMvc.perform(post("/prospects/by-staff")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProspectRequest(firstName, lastName, email, "1155550000", institutionId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void directorCreatesProspectByStaffAndTheActorIsStored() throws Exception {
        Long studentId = createByStaff("by-staff-" + UUID.randomUUID() + "@example.com");

        Long registeredBy = jdbc.queryForObject(
                "select registered_by_person_id from student where id = ?", Long.class, studentId);
        assertThat(registeredBy).isEqualTo(director.getId());
    }

    @Test
    void publicCreationLeavesTheActorNull() throws Exception {
        String email = "public-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/prospects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isCreated());

        Long registeredBy = jdbc.queryForObject(
                "select s.registered_by_person_id from student s join person p on p.id = s.person_id where p.email = ?",
                Long.class, email);
        assertThat(registeredBy).isNull();
    }

    @Test
    void byStaffRejectsAnonymousAndStudentRoles() throws Exception {
        String body = createBody("rejected-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/prospects/by-staff").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/prospects/by-staff")
                        .header("Authorization", "Bearer " + token("ALUMNO"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void byStaffWithAnExistingEmailReturnsConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        createByStaff(email);

        mockMvc.perform(post("/prospects/by-staff")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void directorCanConfirmAProspectWithoutAnyDocument() throws Exception {
        Long studentId = createByStaff("confirm-" + UUID.randomUUID() + "@example.com");
        String dni = Long.toString(10_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 80_000_000L));
        ConfirmProspectRequest request = new ConfirmProspectRequest(dni, null, "Calle", "123", null, "Ciudad", null, null, null, java.time.LocalDate.of(2000, 1, 1), null, null, null, null);

        mockMvc.perform(post("/prospects/{id}/confirm", studentId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("CONFIRMADO"));

        String status = jdbc.queryForObject("select onboarding_status from student where id = ?", String.class, studentId);
        assertThat(status).isEqualTo(StudentOnboardingStatus.CONFIRMADO.name());
    }

    private String confirmBody(String birthDate, String guardian, String emergencyContact) {
        String dni = Long.toString(10_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 80_000_000L));
        return "{\"dni\":\"" + dni + "\",\"street\":\"Mitre\",\"city\":\"Rosario\",\"birthDate\":\"" + birthDate + "\","
                + "\"sex\":\"F\",\"nationality\":\"Argentina\""
                + (guardian == null ? "" : ",\"guardian\":" + guardian)
                + (emergencyContact == null ? "" : ",\"emergencyContact\":" + emergencyContact) + "}";
    }

    private static final String GUARDIAN_JSON =
            "{\"fullName\":\"Luis Paz\",\"documentNumber\":\"20111222\",\"relationship\":\"Padre\",\"phone\":\"+5493410000\",\"email\":\"luis@x.com\"}";
    private static final String EMERGENCY_JSON =
            "{\"name\":\"Marta Paz\",\"relationship\":\"Madre\",\"phone\":\"+5493411111\"}";

    @Test
    void confirmingAMinorWithoutGuardianIsRejectedAndLeavesTheProspectUnconfirmed() throws Exception {
        Long studentId = createByStaff("minor-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/prospects/{id}/confirm", studentId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(LocalDate.now().minusYears(10).toString(), null, EMERGENCY_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUARDIAN_REQUIRED_FOR_MINOR"));

        assertThat(jdbc.queryForObject("select onboarding_status from student where id = ?", String.class, studentId))
                .isEqualTo(StudentOnboardingStatus.PROSPECTO.name());
    }

    @Test
    void confirmingAMinorWithGuardianStoresItAndTheRecordExposesIt() throws Exception {
        Long studentId = createByStaff("minor-ok-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/prospects/{id}/confirm", studentId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(LocalDate.now().minusYears(10).toString(), GUARDIAN_JSON, EMERGENCY_JSON)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select guardian_dni from student where id = ?", String.class, studentId))
                .isEqualTo("20111222");
        mockMvc.perform(get("/students/{id}", studentId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minor").value(true))
                .andExpect(jsonPath("$.guardian.fullName").value("Luis Paz"))
                .andExpect(jsonPath("$.guardian.documentNumber").value("20111222"))
                .andExpect(jsonPath("$.emergencyContact.relationship").value("Madre"));
    }

    @Test
    void confirmingWithoutBirthDateIsRejectedAsBirthDateRequired() throws Exception {
        Long studentId = createByStaff("nobirth-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/prospects/{id}/confirm", studentId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"30111222\",\"street\":\"Mitre\",\"city\":\"Rosario\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIRTH_DATE_REQUIRED"));
    }

    @Test
    void listProspectsReturnsMissingDocumentsAndSkipsConfirmedStudents() throws Exception {
        String email = "list-" + UUID.randomUUID() + "@example.com";
        Long withDocument = createByStaff(email);
        Long confirmedId = transactions.execute(status -> {
            Student confirmed = new Student();
            confirmed.setPerson(persistPerson("ALUMNO"));
            confirmed.setInstitution(entityManager.find(Institution.class, institutionId));
            confirmed.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(confirmed);
            return confirmed.getId();
        });
        transactions.executeWithoutResult(status -> {
            StudentDocument document = new StudentDocument();
            document.setStudent(entityManager.find(Student.class, withDocument));
            document.setType(DocumentType.DNI_FRENTE);
            document.setFilePath("prospects/" + withDocument + "/dni-frente.pdf");
            document.setUploadedAt(LocalDateTime.now());
            entityManager.persist(document);
        });

        String json = mockMvc.perform(get("/prospects").param("search", email)
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var items = objectMapper.readTree(json).get("items");
        var listed = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(item -> item.get("id").asLong() == withDocument)
                .findFirst().orElseThrow();
        assertThat(listed.get("email").asString("")).isEqualTo(email);
        assertThat(listed.get("telefono").asString("")).isEqualTo("1155550000");
        assertThat(listed.get("emailVerified").asBoolean()).isFalse();
        assertThat(objectMapper.convertValue(listed.get("missingDocuments"), java.util.List.class))
                .containsExactlyInAnyOrder("DNI_DORSO", "ANALITICO_SECUNDARIO");
        assertThat(java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .anyMatch(item -> item.get("id").asLong() == confirmedId)).isFalse();
    }

    private tools.jackson.databind.JsonNode listProspects(String search, String page, String size) throws Exception {
        var request = get("/prospects").header("Authorization", "Bearer " + directorToken);
        if (search != null) request = request.param("search", search);
        if (page != null) request = request.param("page", page);
        if (size != null) request = request.param("size", size);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static java.util.List<String> lastNames(tools.jackson.databind.JsonNode page) {
        return java.util.stream.StreamSupport.stream(page.get("items").spliterator(), false)
                .map(item -> item.get("apellido").asString("")).toList();
    }

    @Test
    void listProspectsPaginatesWithStableOrderByLastNameFirstNameAndId() throws Exception {
        String tag = "Zq" + UUID.randomUUID().toString().substring(0, 6);
        createByStaff("Bruno", tag + "B", "p1-" + UUID.randomUUID() + "@example.com");
        createByStaff("Ana", tag + "B", "p2-" + UUID.randomUUID() + "@example.com");
        createByStaff("Ana", tag + "A", "p3-" + UUID.randomUUID() + "@example.com");
        createByStaff("Ana", tag + "B", "p4-" + UUID.randomUUID() + "@example.com");

        var first = listProspects(tag, "0", "3");
        var second = listProspects(tag, "1", "3");

        assertThat(first.get("totalElements").asLong()).isEqualTo(4);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(3);
        assertThat(lastNames(first)).containsExactly(tag + "A", tag + "B", tag + "B");
        assertThat(lastNames(second)).containsExactly(tag + "B");
        var ids = java.util.stream.Stream.concat(
                        java.util.stream.StreamSupport.stream(first.get("items").spliterator(), false),
                        java.util.stream.StreamSupport.stream(second.get("items").spliterator(), false))
                .map(item -> item.get("id").asLong()).toList();
        assertThat(ids).doesNotHaveDuplicates();
        var firstNames = java.util.stream.StreamSupport.stream(first.get("items").spliterator(), false)
                .map(item -> item.get("nombre").asString("")).toList();
        assertThat(firstNames).containsExactly("Ana", "Ana", "Ana");
        assertThat(ids.get(1)).isLessThan(ids.get(2));
    }

    @Test
    void listProspectsSearchIgnoresCaseAndAccentsOverNameLastNameAndEmail() throws Exception {
        String tag = "Qx" + UUID.randomUUID().toString().substring(0, 6).toLowerCase();
        Long byName = createByStaff("Ángela" + tag, "Gómez", "n-" + UUID.randomUUID() + "@example.com");
        Long byLastName = createByStaff("Luis", "Muñoz" + tag, "l-" + UUID.randomUUID() + "@example.com");
        Long byEmail = createByStaff("Rita", "Sosa", "mail-" + tag + "@example.com");

        assertThat(ids(listProspects("angela" + tag.toUpperCase(), null, null))).containsExactly(byName);
        assertThat(ids(listProspects("MUNOZ" + tag, null, null))).containsExactly(byLastName);
        assertThat(ids(listProspects("MAIL-" + tag.toUpperCase(), null, null))).containsExactly(byEmail);
        assertThat(ids(listProspects("gomez ángela" + tag, null, null))).containsExactly(byName);
        assertThat(listProspects("nadie-" + tag + "-zz", null, null).get("totalElements").asLong()).isZero();
    }

    @Test
    void listProspectsCapsSizeAtOneHundredAndDefaultsToTwentyFive() throws Exception {
        var capped = listProspects(null, null, "500");
        var defaults = listProspects(null, null, null);

        assertThat(capped.get("size").asInt()).isEqualTo(100);
        assertThat(capped.get("items").size()).isLessThanOrEqualTo(100);
        assertThat(defaults.get("size").asInt()).isEqualTo(25);
        assertThat(defaults.get("page").asInt()).isZero();
    }

    private static java.util.List<Long> ids(tools.jackson.databind.JsonNode page) {
        return java.util.stream.StreamSupport.stream(page.get("items").spliterator(), false)
                .map(item -> item.get("id").asLong()).toList();
    }

    @Test
    void listProspectsIsForbiddenForDocente() throws Exception {
        mockMvc.perform(get("/prospects").header("Authorization", "Bearer " + token("DOCENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/prospects")).andExpect(status().isUnauthorized());
    }
}
