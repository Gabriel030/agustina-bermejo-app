package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;
import com.red_escuela.backend.attendance.exception.AttendanceStatusNotRecordableException;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.ApproveAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RequestAttendanceChangeCommand;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RequestAttendanceChangeUseCase;
import com.red_escuela.backend.attendance.exception.AttendanceAlreadyRecordedException;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.dto.InstructorRole;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SlowTest(reason = "Boots the first Spring context of its JVM, so its time includes the one-time start-up, and runs the attendance scenarios")
class AttendanceUseCaseTest extends AbstractIntegrationTest {
    private static final LocalDate CLASS_DAY = LocalDate.of(2025, 9, 15);


    @Autowired
    private LoadProgramSectionAttendanceUseCase loadProgramSectionAttendanceUseCase;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private ListProgramEnrollmentAttendanceUseCase listProgramEnrollmentAttendanceUseCase;

    @Autowired
    private JustifyAttendanceUseCase justifyAttendanceUseCase;

    @Autowired
    private SubmitAttendanceProofUseCase submitAttendanceProofUseCase;

    @Autowired
    private ApproveAttendanceJustificationUseCase approveAttendanceJustificationUseCase;

    @Autowired
    private RejectAttendanceJustificationUseCase rejectAttendanceJustificationUseCase;

    @Autowired
    private CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private com.red_escuela.backend.attendance.service.SectionRosterBuilder sectionRosterBuilder;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Autowired
    private RequestAttendanceChangeUseCase requestAttendanceChangeUseCase;

    @Autowired
    private ApproveAttendanceChangeRequestUseCase approveAttendanceChangeRequestUseCase;

    @Autowired
    private GetProgramSectionRosterUseCase getProgramSectionRosterUseCase;

    @AfterEach
    void clearSecurityContext() {
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
        subject.setCode(TestSubjectCodes.next());
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
        section.setMaxAbsenceRate(maxAbsencePercentage == null ? null : Percentage.ofPoints(BigDecimal.valueOf(maxAbsencePercentage)));
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private void assignInstructor(SubjectSection section, Instructor instructor) {
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
    void instructorNotInSectionCannotLoadAttendance() {
        Institution institution = persistInstitution();
        Instructor instructorAjeno = persistInstructor("80000001");
        Subject subject = persistSubject("MateriaAsistenciaAjena");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        entityManager.flush();

        CargarAsistenciaRequest request = new CargarAsistenciaRequest(CLASS_DAY, List.of(), null, null);

        assertThatThrownBy(() -> loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructorAjeno.getId(), request)))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void loadingTheSameAttendanceTwiceOnSameDayDoesNotDuplicateTheRow() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000002", institution);
        Instructor instructor = persistInstructor("80000003");
        Subject subject = persistSubject("MateriaAsistenciaDuplicada");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraAsistenciaDuplicada");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        LocalDate classDate = CLASS_DAY;
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(classDate, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(classDate, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));

        List<Attendance> asistencias = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId());
        assertThat(asistencias).hasSize(1);
        assertThat(asistencias.get(0).getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void duplicateWithinSameBulkPayloadDoesNotCreateTwoRows() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000004", institution);
        Instructor instructor = persistInstructor("80000005");
        Subject subject = persistSubject("MateriaBulkDuplicado");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraBulkDuplicado");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        CargarAsistenciaRequest request = new CargarAsistenciaRequest(CLASS_DAY, List.of(
                new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT),
                new AttendanceEntry(enrollment.getId(), AttendanceStatus.LATE)), null, null);

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(), request));

        List<Attendance> asistencias = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId());
        assertThat(asistencias).hasSize(1);
        assertThat(asistencias.get(0).getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void justifyingARowThatIsNotAbsentFails() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000006", institution);
        Instructor instructor = persistInstructor("80000007");
        Subject subject = persistSubject("MateriaJustificacionInvalida");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraJustificacionInvalida");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);

        Person preceptor = persistPersonBase("80000008");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        assertThatThrownBy(() -> justifyAttendanceUseCase.justify(new JustifyAttendanceCommand(attendance.getId(), "motivo", null)))
                .isInstanceOf(CannotJustifyException.class);
    }

    @Test
    void justifyingAnAbsenceMovesItToJustified() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000009", institution);
        Instructor instructor = persistInstructor("80000010");
        Subject subject = persistSubject("MateriaJustificacionValida");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraJustificacionValida");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);

        Person preceptor = persistPersonBase("80000011");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        Attendance justificada = justifyAttendanceUseCase.justify(new JustifyAttendanceCommand(attendance.getId(), "motivo médico", null));
        assertThat(justificada.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(justificada.getJustificationReason()).isEqualTo("motivo médico");
        assertThat(justificada.getJustifiedAt()).isNotNull();
    }

    @Test
    void studentWithZeroClassesTakenDoesNotGiveFalsePositiveOfRisk() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000012", institution);
        Subject subject = persistSubject("MateriaSinClases");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, 25.0);
        Program program = persistProgram("CarreraSinClases");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        AttendanceRiskResponse riesgo = calculateProgramEnrollmentAttendanceRiskUseCase.calculate(enrollment.getId());
        assertThat(riesgo.calculable()).isTrue();
        assertThat(riesgo.libre()).isFalse();
        assertThat(riesgo.absencePercent()).isNull();
    }

    @Test
    void studentExceedingMaxAbsencePercentageIsAtRisk() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000013", institution);
        Instructor instructor = persistInstructor("80000014");
        Subject subject = persistSubject("MateriaLibrePorInasistencias");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, 25.0);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraLibrePorInasistencias");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        LocalDate d = CLASS_DAY;
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(3), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(2), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(1), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.LATE)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));

        AttendanceRiskResponse riesgo = calculateProgramEnrollmentAttendanceRiskUseCase.calculate(enrollment.getId());
        assertThat(riesgo.calculable()).isTrue();
        assertThat(riesgo.libre()).isTrue();
        assertThat(riesgo.absencePercent().points()).isEqualByComparingTo("50");
    }

    @Test
    void submittingProofLeavesAttendanceInJustificationPendingNotJustified() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000015", institution);
        Instructor instructor = persistInstructor("80000016");
        Subject subject = persistSubject("MateriaComprobantePendiente");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraComprobantePendiente");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);

        Attendance pendiente = submitAttendanceProofUseCase.submit(new SubmitAttendanceProofCommand(attendance.getId(), "motivo médico", "students/1/comprobante.pdf"));
        assertThat(pendiente.getStatus()).isEqualTo(AttendanceStatus.JUSTIFICATION_PENDING);
        assertThat(pendiente.getJustifiedAt()).isNull();
    }

    @Test
    void approvingAPendingJustificationMovesItToJustified() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000017", institution);
        Instructor instructor = persistInstructor("80000018");
        Subject subject = persistSubject("MateriaAprobarJustificacion");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraAprobarJustificacion");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);
        submitAttendanceProofUseCase.submit(new SubmitAttendanceProofCommand(attendance.getId(), "motivo médico", "students/1/comprobante.pdf"));

        Person preceptor = persistPersonBase("80000019");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        Attendance aprobada = approveAttendanceJustificationUseCase.approve(attendance.getId());
        assertThat(aprobada.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(aprobada.getJustifiedAt()).isNotNull();
    }

    @Test
    void rejectingAPendingJustificationReturnsItToAbsent() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000020", institution);
        Instructor instructor = persistInstructor("80000021");
        Subject subject = persistSubject("MateriaRechazarJustificacion");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraRechazarJustificacion");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);
        submitAttendanceProofUseCase.submit(new SubmitAttendanceProofCommand(attendance.getId(), "motivo médico", "students/1/comprobante.pdf"));

        Attendance rechazada = rejectAttendanceJustificationUseCase.reject(attendance.getId());
        assertThat(rechazada.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void approvingAnAttendanceWithoutPendingJustificationFails() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000022", institution);
        Instructor instructor = persistInstructor("80000023");
        Subject subject = persistSubject("MateriaAprobarSinPendiente");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraAprobarSinPendiente");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);

        Person preceptor = persistPersonBase("80000024");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(preceptor.getId().toString(), null));

        assertThatThrownBy(() -> approveAttendanceJustificationUseCase.approve(attendance.getId()))
                .isInstanceOf(CannotJustifyException.class);
    }

    @Test
    void aPendingJustificationStillCountsAsAbsentForRisk() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000025", institution);
        Instructor instructor = persistInstructor("80000026");
        Subject subject = persistSubject("MateriaPendienteCuentaComoAusente");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, 25.0);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraPendienteCuentaComoAusente");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        LocalDate d = CLASS_DAY;
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(3), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(2), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(1), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));
        Attendance ausente = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ABSENT).findFirst().orElseThrow();
        submitAttendanceProofUseCase.submit(new SubmitAttendanceProofCommand(ausente.getId(), "motivo médico", "students/1/comprobante.pdf"));

        AttendanceRiskResponse riesgo = calculateProgramEnrollmentAttendanceRiskUseCase.calculate(enrollment.getId());
        assertThat(riesgo.absencePercent().points()).isEqualByComparingTo("25");
    }

    @Test
    void studentWithAbsencesBelowTheMaxIsNotAtRisk() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000027", institution);
        Instructor instructor = persistInstructor("80000028");
        Term term = persistTerm();
        SubjectSection section = persistSection(persistSubject("MateriaDentroDelLimite"), term, 25.0);
        assignInstructor(section, instructor);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("CarreraDentroDelLimite"));
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        LocalDate d = CLASS_DAY;
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(3), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        for (int i = 0; i < 3; i++) {
            loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                    new CargarAsistenciaRequest(d.minusDays(i), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));
        }

        AttendanceRiskResponse riesgo = calculateProgramEnrollmentAttendanceRiskUseCase.calculate(enrollment.getId());
        assertThat(riesgo.libre()).isFalse();
        assertThat(riesgo.absencePercent().points()).isEqualByComparingTo("25");
    }

    @Test
    void attendanceSummaryShowsTheAttendancePercentageOfEachSubjectAndNothingWithoutClasses() {
        Institution institution = persistInstitution();
        Student student = persistStudent("80000029", institution);
        Instructor instructor = persistInstructor("80000030");
        Term term = persistTerm();
        SubjectSection conClases = persistSection(persistSubject("MateriaConClases"), term, 25.0);
        SubjectSection sinClases = persistSection(persistSubject("MateriaSinRegistros"), term, 25.0);
        assignInstructor(conClases, instructor);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("CarreraResumen"));
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, conClases);
        persistEnrollment(student, programEnrollment, sinClases);
        entityManager.flush();

        LocalDate d = CLASS_DAY;
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(conClases.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d.minusDays(1), List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(conClases.getId(), instructor.getId(),
                new CargarAsistenciaRequest(d, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null)));

        List<SubjectAttendanceSummaryResponse> summary = programEnrollmentService.attendanceSummaryBySubject(programEnrollment.getId());
        assertThat(summary).extracting(SubjectAttendanceSummaryResponse::subjectName,
                        row -> row.attendancePercent() == null ? null : row.attendancePercent().points().doubleValue())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("MateriaConClases", 50.0),
                        org.assertj.core.groups.Tuple.tuple("MateriaSinRegistros", null));
    }

    private SubjectSectionEnrollment enrollInNewSection(String dni, String instructorDni, String name, Instructor[] instructorHolder) {
        Institution institution = persistInstitution();
        Student student = persistStudent(dni, institution);
        Instructor instructor = persistInstructor(instructorDni);
        SubjectSection section = persistSection(persistSubject(name), persistTerm(), 25.0);
        assignInstructor(section, instructor);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("Carrera" + name));
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();
        instructorHolder[0] = instructor;
        return enrollment;
    }

    private void load(SubjectSectionEnrollment enrollment, Instructor instructor, LocalDate date, AttendanceStatus status) {
        loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(enrollment.getSubjectSection().getId(), instructor.getId(),
                new CargarAsistenciaRequest(date, List.of(new AttendanceEntry(enrollment.getId(), status)), null, null)));
    }

    @Test
    void takingAttendanceOnAHolidayIsRejected() {
        Instructor[] instructor = new Instructor[1];
        SubjectSectionEnrollment enrollment = enrollInNewSection("80000031", "80000032", "MateriaFeriado", instructor);
        Holiday holiday = new Holiday();
        holiday.setDate(CLASS_DAY);
        holiday.setDescription("Feriado de prueba");
        entityManager.persist(holiday);
        entityManager.flush();

        assertThatThrownBy(() -> load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.PRESENT))
                .isInstanceOf(ClassOnHolidayException.class);
        assertThat(listProgramEnrollmentAttendanceUseCase.list(enrollment.getId())).isEmpty();
    }

    @Test
    void changingAnAlreadyRecordedAttendanceIsRejected() {
        Instructor[] instructor = new Instructor[1];
        SubjectSectionEnrollment enrollment = enrollInNewSection("80000033", "80000034", "MateriaSobrescritura", instructor);
        load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.LATE);

        assertThatThrownBy(() -> load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.ABSENT))
                .isInstanceOf(AttendanceAlreadyRecordedException.class);
        assertThat(listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()))
                .extracting(Attendance::getStatus).containsExactly(AttendanceStatus.LATE);
    }

    @Test
    void teacherCannotRecordJustifiedOrPendingJustificationWhenTakingAttendance() {
        Instructor[] instructor = new Instructor[1];
        SubjectSectionEnrollment enrollment = enrollInNewSection("80000041", "80000042", "MateriaSinAtajoJustificada", instructor);

        assertThatThrownBy(() -> load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.JUSTIFIED))
                .isInstanceOf(AttendanceStatusNotRecordableException.class);
        assertThatThrownBy(() -> load(enrollment, instructor[0], CLASS_DAY.minusDays(7), AttendanceStatus.JUSTIFICATION_PENDING))
                .isInstanceOf(AttendanceStatusNotRecordableException.class);
        assertThat(listProgramEnrollmentAttendanceUseCase.list(enrollment.getId())).isEmpty();
    }

    @Test
    void aChangeRequestCannotAskForAJustifiedStatus() {
        Instructor[] instructor = new Instructor[1];
        SubjectSectionEnrollment enrollment = enrollInNewSection("80000043", "80000044", "MateriaCambioAJustificada", instructor);
        load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.ABSENT);
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);

        assertThatThrownBy(() -> requestAttendanceChangeUseCase.request(new RequestAttendanceChangeCommand(
                attendance.getId(), instructor[0], new CreateAttendanceChangeRequestRequest(AttendanceStatus.JUSTIFIED, "trajo certificado"))))
                .isInstanceOf(AttendanceStatusNotRecordableException.class);
    }

    @Test
    void approvingAPendingChangeRequestToJustifiedFailsAndKeepsTheAttendance() {
        Instructor[] instructor = new Instructor[1];
        SubjectSectionEnrollment enrollment = enrollInNewSection("80000045", "80000046", "MateriaAprobarAJustificada", instructor);
        load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.ABSENT);
        Attendance attendance = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);
        AttendanceChangeRequest pending = new AttendanceChangeRequest();
        pending.setAttendance(attendance);
        pending.setRequestedBy(instructor[0]);
        pending.setPreviousStatus(AttendanceStatus.ABSENT);
        pending.setRequestedStatus(AttendanceStatus.JUSTIFIED);
        pending.setReason("pedido viejo");
        pending.setStatus(AttendanceChangeRequestStatus.PENDING);
        entityManager.persist(pending);
        Person preceptor = persistPersonBase("80000047");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(preceptor.getId().toString(), null));
        entityManager.flush();

        assertThatThrownBy(() -> approveAttendanceChangeRequestUseCase.approve(pending.getId()))
                .isInstanceOf(AttendanceStatusNotRecordableException.class);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void aJustifiedAbsenceIsNotCountedAsAnAbsenceInTheRoster() {
        Instructor[] instructor = new Instructor[1];
        SubjectSectionEnrollment enrollment = enrollInNewSection("80000048", "80000049", "MateriaRosterJustificada", instructor);
        load(enrollment, instructor[0], CLASS_DAY, AttendanceStatus.ABSENT);
        load(enrollment, instructor[0], CLASS_DAY.minusDays(7), AttendanceStatus.ABSENT);
        Attendance first = listProgramEnrollmentAttendanceUseCase.list(enrollment.getId()).get(0);
        Person preceptor = persistPersonBase("80000050");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(preceptor.getId().toString(), null));
        justifyAttendanceUseCase.justify(new JustifyAttendanceCommand(first.getId(), "certificado médico", null));
        entityManager.flush();

        List<RosterEntryResponse> roster = getProgramSectionRosterUseCase.get(
                new GetProgramSectionRosterCommand(enrollment.getSubjectSection().getId(), instructor[0].getId()));

        assertThat(roster).singleElement().satisfies(row -> {
            assertThat(row.absenceCount()).isEqualTo(1);
            assertThat(row.attendancePercent().points()).isEqualByComparingTo("50");
        });
    }

    @Test
    void aRosterOfFortyStudentsIsBuiltWithAtMostThreeQueries() {
        Institution institution = persistInstitution();
        Instructor instructor = persistInstructor("80000100");
        SubjectSection section = persistSection(persistSubject("MateriaRoster40"), persistTerm(), 25.0);
        assignInstructor(section, instructor);
        List<SubjectSectionEnrollment> enrollments = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Student student = persistStudent("8100" + String.format("%04d", i), institution);
            ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("CarreraRoster40-" + i));
            enrollments.add(persistEnrollment(student, programEnrollment, section));
        }
        entityManager.flush();
        for (int day = 0; day < 4; day++) {
            int currentDay = day;
            List<AttendanceEntry> entries = new java.util.ArrayList<>();
            for (int i = 0; i < enrollments.size(); i++) {
                AttendanceStatus status = (i + currentDay) % 4 == 0 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
                entries.add(new AttendanceEntry(enrollments.get(i).getId(), status));
            }
            loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(section.getId(), instructor.getId(),
                    new CargarAsistenciaRequest(CLASS_DAY.minusDays(7L * currentDay), entries, null, null)));
        }
        entityManager.flush();
        entityManager.clear();

        List<RosterEntryResponse> roster = com.red_escuela.backend.shared.persistence.QueryCountAssertions.assertAtMostQueries(
                entityManagerFactory, 3, () -> sectionRosterBuilder.buildForProgramSection(section.getId()));

        assertThat(roster).hasSize(40);
        assertThat(roster).allSatisfy(row -> {
            assertThat(row.absenceCount()).isEqualTo(1);
            assertThat(row.attendancePercent().points()).isEqualByComparingTo("75");
        });
    }
}
