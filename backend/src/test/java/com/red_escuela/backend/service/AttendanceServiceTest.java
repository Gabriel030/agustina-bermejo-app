package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AttendanceEntry;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Attendance;
import com.red_escuela.backend.entity.AttendanceStatus;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.SubjectSectionInstructor;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.CannotJustifyException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica: no-duplicado de asistencia (misma fecha, incluso dos entries del mismo alumno en
 * el MISMO payload bulk), autorización de "comisión propia" del Docente, y el cálculo derivado
 * de "libre por inasistencias" (nunca persistido — decisión 5 de la spec técnica; LATE cuenta
 * como presente, JUSTIFIED no cuenta ni a favor ni en contra).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearContextOfSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPersonBase(String dni) {
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
    }

    private Student persistStudent(String dni, Institution institution) {
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Instructor persistInstructor(String dni) {
        Instructor instructor = new Instructor();
        instructor.setPerson(persistPersonBase(dni));
        entityManager.persist(instructor);
        return instructor;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSection(Subject subject, Term term, Double maxAbsencePercentage) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setMaxAbsencePercentage(maxAbsencePercentage);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private void asignarDocente(SubjectSection section, Instructor instructor) {
        SubjectSectionInstructor asignacion = new SubjectSectionInstructor();
        asignacion.setSection(section);
        asignacion.setInstructor(instructor);
        asignacion.setRole(InstructorRole.LEAD);
        entityManager.persist(asignacion);
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private SubjectSectionEnrollment persistEnrollment(Student student, ProgramEnrollment programEnrollment, SubjectSection section) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    void teacherThatNotBelongsToTheSectionNotCanLoadAttendance() {
        Institution institution = persistInstitution();
        Instructor instructorAjeno = persistInstructor("80000001");
        Subject subject = persistSubject("MateriaAsistenciaAjena");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        entityManager.flush();

        CargarAsistenciaRequest request = new CargarAsistenciaRequest(LocalDate.now(), List.of(), null, null);

        assertThatThrownBy(() -> attendanceService.cargarAsistenciaProgramSection(section.getId(), instructorAjeno.getId(), request))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void loadAttendanceTwoTimesTheSameDayUpdatesTheRowExistingNotTheDuplicates() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000002", institution);
        Instructor instructor = persistInstructor("80000003");
        Subject subject = persistSubject("MateriaAsistenciaDuplicada");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraAsistenciaDuplicada");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        LocalDate classDate = LocalDate.now();
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(classDate, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(classDate, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));

        List<Attendance> asistencias = attendanceService.listByProgramEnrollment(enrollment.getId());
        assertThat(asistencias).hasSize(1);
        assertThat(asistencias.get(0).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void duplicatedWithinOfTheSamePayloadBulkNotCreatesTwoRows() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000004", institution);
        Instructor instructor = persistInstructor("80000005");
        Subject subject = persistSubject("MateriaBulkDuplicado");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraBulkDuplicado");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        // Mismo enrollmentId dos veces en el MISMO request: la última entry tiene que ganar,
        // sin create dos filas de asistencia para la misma clase.
        CargarAsistenciaRequest request = new CargarAsistenciaRequest(LocalDate.now(), List.of(
                new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT),
                new AttendanceEntry(enrollment.getId(), AttendanceStatus.LATE)), null, null);

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(), request);

        List<Attendance> asistencias = attendanceService.listByProgramEnrollment(enrollment.getId());
        assertThat(asistencias).hasSize(1);
        assertThat(asistencias.get(0).getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void justifyARowThatNotIsAbsentFails() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000006", institution);
        Instructor instructor = persistInstructor("80000007");
        Subject subject = persistSubject("MateriaJustificacionInvalida");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraJustificacionInvalida");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(LocalDate.now(), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));
        Attendance attendance = attendanceService.listByProgramEnrollment(enrollment.getId()).get(0);

        Person preceptor = persistPersonBase("80000008");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        assertThatThrownBy(() -> attendanceService.justificar(attendance.getId(), "motivo", null))
                .isInstanceOf(CannotJustifyException.class);
    }

    @Test
    void justifyAAbsenceTheGoesToJustified() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000009", institution);
        Instructor instructor = persistInstructor("80000010");
        Subject subject = persistSubject("MateriaJustificacionValida");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraJustificacionValida");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(LocalDate.now(), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        Attendance attendance = attendanceService.listByProgramEnrollment(enrollment.getId()).get(0);

        Person preceptor = persistPersonBase("80000011");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        Attendance justificada = attendanceService.justificar(attendance.getId(), "motivo médico", null);
        assertThat(justificada.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(justificada.getJustificationReason()).isEqualTo("motivo médico");
        assertThat(justificada.getJustifiedAt()).isNotNull();
    }

    @Test
    void studentWithZeroClassesTakenNotGivesFalsePositiveOfOutOfStanding() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000012", institution);
        Subject subject = persistSubject("MateriaSinClases");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, 0.25);
        Program program = persistProgram("CarreraSinClases");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        AttendanceRiskResponse riesgo = attendanceService.calculateDropoutRiskByProgramEnrollment(enrollment.getId());
        assertThat(riesgo.calculable()).isTrue();
        assertThat(riesgo.libre()).isFalse();
        assertThat(riesgo.porcentajeAusentismo()).isEqualTo(0.0);
    }

    @Test
    void studentThatExceedsThePercentageMaximumOfAbsenteeismRemainsOutOfStanding() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000013", institution);
        Instructor instructor = persistInstructor("80000014");
        Subject subject = persistSubject("MateriaLibrePorInasistencias");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, 0.25);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraLibrePorInasistencias");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        // 4 clases: 2 ausentes (50%) > 25% permitido. LATE y JUSTIFIED no deberían contar como
        // ausentes (decisión de producto), así que se agregan también para verificar que no
        // distorsionan el porcentaje.
        LocalDate d = LocalDate.now();
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(3), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(2), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(1), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.LATE)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));

        AttendanceRiskResponse riesgo = attendanceService.calculateDropoutRiskByProgramEnrollment(enrollment.getId());
        assertThat(riesgo.calculable()).isTrue();
        assertThat(riesgo.libre()).isTrue();
        assertThat(riesgo.porcentajeAusentismo()).isEqualTo(0.5);
    }

    @Test
    void uploadReceiptLeavesTheAttendanceInJustificationPendingNotInJustified() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000015", institution);
        Instructor instructor = persistInstructor("80000016");
        Subject subject = persistSubject("MateriaComprobantePendiente");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraComprobantePendiente");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(LocalDate.now(), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        Attendance attendance = attendanceService.listByProgramEnrollment(enrollment.getId()).get(0);

        Attendance pendiente = attendanceService.uploadJustificationProof(attendance.getId(), "motivo médico", "students/1/comprobante.pdf");
        assertThat(pendiente.getStatus()).isEqualTo(AttendanceStatus.JUSTIFICATION_PENDING);
        assertThat(pendiente.getJustifiedAt()).isNull();
    }

    @Test
    void approveAJustificationPendingTheGoesToJustified() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000017", institution);
        Instructor instructor = persistInstructor("80000018");
        Subject subject = persistSubject("MateriaAprobarJustificacion");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraAprobarJustificacion");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(LocalDate.now(), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        Attendance attendance = attendanceService.listByProgramEnrollment(enrollment.getId()).get(0);
        attendanceService.uploadJustificationProof(attendance.getId(), "motivo médico", "students/1/comprobante.pdf");

        Person preceptor = persistPersonBase("80000019");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        Attendance aprobada = attendanceService.approveJustification(attendance.getId());
        assertThat(aprobada.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(aprobada.getJustifiedAt()).isNotNull();
    }

    @Test
    void rejectAJustificationPendingTheGoesBackToAbsent() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000020", institution);
        Instructor instructor = persistInstructor("80000021");
        Subject subject = persistSubject("MateriaRechazarJustificacion");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraRechazarJustificacion");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(LocalDate.now(), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        Attendance attendance = attendanceService.listByProgramEnrollment(enrollment.getId()).get(0);
        attendanceService.uploadJustificationProof(attendance.getId(), "motivo médico", "students/1/comprobante.pdf");

        Attendance rechazada = attendanceService.rejectJustification(attendance.getId());
        assertThat(rechazada.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void approveAAttendanceWithoutJustificationPendingFails() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000022", institution);
        Instructor instructor = persistInstructor("80000023");
        Subject subject = persistSubject("MateriaAprobarSinPendiente");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraAprobarSinPendiente");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(LocalDate.now(), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        Attendance attendance = attendanceService.listByProgramEnrollment(enrollment.getId()).get(0);

        Person preceptor = persistPersonBase("80000024");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        assertThatThrownBy(() -> attendanceService.approveJustification(attendance.getId()))
                .isInstanceOf(CannotJustifyException.class);
    }

    @Test
    void aJustificationPendingRemainsCountingAsAbsentForTheRisk() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000025", institution);
        Instructor instructor = persistInstructor("80000026");
        Subject subject = persistSubject("MateriaPendienteCuentaComoAusente");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, 0.25);
        asignarDocente(section, instructor);
        Program program = persistProgram("CarreraPendienteCuentaComoAusente");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        LocalDate d = LocalDate.now();
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(3), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(2), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(1), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));
        attendanceService.cargarAsistenciaProgramSection(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));
        Attendance ausente = attendanceService.listByProgramEnrollment(enrollment.getId()).stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ABSENT).findFirst().orElseThrow();
        attendanceService.uploadJustificationProof(ausente.getId(), "motivo médico", "students/1/comprobante.pdf");

        AttendanceRiskResponse riesgo = attendanceService.calculateDropoutRiskByProgramEnrollment(enrollment.getId());
        assertThat(riesgo.porcentajeAusentismo()).isEqualTo(0.25);
    }
}
