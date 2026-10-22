package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReenrollmentControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    private String tokenWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();

        return jwtService.generateAccessToken(person);
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(dni);
        person.setDni(Long.toString(Math.abs((long) (dni).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person = personRepository.save(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private void persistOldWithdrawnCycleWithDebt(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        enrollment.setPaymentStatus(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        enrollment = programEnrollmentRepository.save(enrollment);

        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(3)).dueOn(LocalDate.now().minusMonths(3)).withBaseAmount(ars(5000)).build();
        BillingTestFixtures.markInvoiceAsOverdue(invoice);
    }

    @Test
    @Tag("C-18")
    @Tag("D-251")
    void reinstatingAWithdrawnEnrollmentIsNotAnOperationAnymore() throws Exception {
        Student student = persistStudent("88020001");
        Program program = persistProgram();
        persistOldWithdrawnCycleWithDebt(student, program);
        String token = tokenWithRole("administrativo.reenrollment." + System.nanoTime(), "ADMINISTRATIVO");

        mockMvc.perform(post("/students/{studentId}/reenrollment/program/{programId}", student.getId(), program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/students/{studentId}/reenrollment/course/{courseId}", student.getId(), 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Tag("C-19")
    @Tag("D-251")
    void waiveDecisionIsNotAcceptedAndTheDebtIsNeverForgiven() throws Exception {
        Student student = persistStudent("88020002");
        Program program = persistProgram();
        persistOldWithdrawnCycleWithDebt(student, program);
        String token = tokenWithRole("director.reenrollment." + System.nanoTime(), "DIRECTOR");

        mockMvc.perform(post("/students/{studentId}/reenrollment/program/{programId}", student.getId(), program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"decision\":\"WAIVE\"}"))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        assertThat(invoiceRepository.existsByStudentIdAndPaymentStatus(student.getId(), InvoicePaymentStatus.OVERDUE)).isTrue();
    }

    @Test
    @Tag("C-16")
    @Tag("D-142")
    void aProgramEnrollmentWithOutstandingDebtAnswersConflictWithTheReenrollmentCode() throws Exception {
        Student student = persistStudent("88020003");
        Program program = persistProgram();
        persistOldWithdrawnCycleWithDebt(student, program);
        String token = tokenWithRole("preceptor.reenrollment." + System.nanoTime(), "PRECEPTOR");

        mockMvc.perform(post("/students/{id}/program-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"programId\":" + program.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSTANDING_DEBT_BLOCKS_REENROLLMENT"))
                .andExpect(jsonPath("$.violations[0].code").value("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT"));
    }
}
