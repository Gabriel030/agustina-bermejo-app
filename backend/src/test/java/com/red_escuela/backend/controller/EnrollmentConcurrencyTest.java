package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.ProgramSubjectRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectSectionRepository;
import com.red_escuela.backend.repository.TermRepository;
import com.red_escuela.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regresión del hallazgo crítico de la auditoría post-PR #7: sin lock, dos altas
 * concurrentes a una comisión con capacity=1 podían leer el mismo conteo de "ocupadas"
 * antes de que cualquiera hiciera commit, y ambas terminar ENROLLED. Deliberadamente SIN
 * @Transactional a nivel de clase: cada request necesita su propia transacción real para
 * que el lock pesimista (SubjectSectionRepository.findByIdForUpdate) tenga sentido —
 * si todo corriera en una única transacción de test, ambos hilos compartirían la misma
 * conexión/lock y no se probaría nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnrollmentConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private SubjectRepository subjectRepository;
    @Autowired
    private ProgramSubjectRepository programSubjectRepository;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private SubjectSectionRepository subjectSectionRepository;
    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;

    private String tokenWithRole(String username, String roleName) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        person = personRepository.save(person);
        return jwtService.generateAccessToken(person);
    }

    private Student persistStudent(String dni) {
        Address address = addressRepository.save(new Address());
        Institution institution = institutionRepository.save(new Institution());
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
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

    @Test
    void dosAltasConcurrentesAUnaComisionConCupo1NuncaDejanADosAlumnosEnrolled() throws Exception {
        Student estudiante1 = persistStudent("70000001");
        Student estudiante2 = persistStudent("70000002");

        Subject subject = new Subject();
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subject.setName("MateriaConcurrencia-" + System.nanoTime());
        subject = subjectRepository.save(subject);

        Program program = new Program();
        program.setName("CarreraConcurrencia-" + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        program = programRepository.save(program);

        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(10);
        programSubject.setCurricularYear(1);
        programSubject.setCurricularTerm(CurricularTerm.FIRST);
        programSubject.setStatus(StatusType.ACTIVE);
        programSubjectRepository.save(programSubject);

        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        SubjectSection section = new SubjectSection();
        section.setName("Comisión concurrencia");
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(1);
        section.setStatus(StatusType.ACTIVE);
        section = subjectSectionRepository.save(section);

        ProgramEnrollment enrollment1 = new ProgramEnrollment();
        enrollment1.setStudent(estudiante1);
        enrollment1.setProgram(program);
        enrollment1.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment1 = programEnrollmentRepository.save(enrollment1);

        ProgramEnrollment enrollment2 = new ProgramEnrollment();
        enrollment2.setStudent(estudiante2);
        enrollment2.setProgram(program);
        enrollment2.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment2 = programEnrollmentRepository.save(enrollment2);

        String token = tokenWithRole("preceptor-concurrencia", "PRECEPTOR");
        String body = objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(section.getId()));
        Long peId1 = enrollment1.getId();
        Long peId2 = enrollment2.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);

        Runnable llamada1 = () -> {
            try {
                listos.countDown();
                arranca.await();
                mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", peId1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body));
            } catch (Exception ignored) {
                // El resultado se verifica consultando la base al final, no acá.
            }
        };
        Runnable llamada2 = () -> {
            try {
                listos.countDown();
                arranca.await();
                mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", peId2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body));
            } catch (Exception ignored) {
                // Idem.
            }
        };

        try {
            executor.submit(llamada1);
            executor.submit(llamada2);
            listos.await(5, TimeUnit.SECONDS);
            arranca.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            List<SubjectSectionEnrollment> filas = subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(
                    section.getId(), List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED));
            long enrolled = filas.stream().filter(f -> f.getStatus() == EnrollmentStatus.ENROLLED).count();
            long waitlisted = filas.stream().filter(f -> f.getStatus() == EnrollmentStatus.WAITLISTED).count();

            assertThat(filas).hasSize(2);
            assertThat(enrolled).isEqualTo(1L);
            assertThat(waitlisted).isEqualTo(1L);
        } finally {
            // Sin @Transactional a nivel de clase (necesario para que el lock pesimista se
            // pruebe con transacciones reales por request), nada se revierte solo: hay que
            // limpiar a mano para no contaminar el conteo de otros tests que listan "todos
            // los Student" sin filtro sobre el mismo contenedor Postgres compartido.
            subjectSectionEnrollmentRepository.deleteAll(
                    subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(
                            section.getId(), List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED)));
            programEnrollmentRepository.delete(enrollment1);
            programEnrollmentRepository.delete(enrollment2);
            subjectSectionRepository.delete(section);
            programSubjectRepository.delete(programSubject);
            termRepository.delete(term);
            programRepository.delete(program);
            subjectRepository.delete(subject);
            studentRepository.delete(estudiante1);
            studentRepository.delete(estudiante2);
        }
    }
}
