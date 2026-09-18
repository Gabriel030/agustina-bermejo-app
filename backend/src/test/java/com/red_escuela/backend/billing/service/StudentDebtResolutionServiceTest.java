package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.billing.exception.StudentInDebtException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;

// Fase 6.4: tests 44-49, 118-120. NO @Transactional a propósito (PaymentService/InvoiceFactory
// necesitan su propia transacción real).
@SpringBootTest
@ActiveProfiles("test")
class StudentDebtResolutionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StudentDebtResolutionService studentDebtResolutionService;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

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
    private com.red_escuela.backend.billing.repository.ScholarshipRepository scholarshipRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Program persistPrograma() {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private ProgramEnrollment persistOldWithdrawnCycle(Student student, Program program, BigDecimal deuda) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        enrollment.setBillingStanding(EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        enrollment = programEnrollmentRepository.save(enrollment);

        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now().minusMonths(3));
        invoice.setDueDate(LocalDate.now().minusMonths(3));
        invoice.setBaseAmount(deuda);
        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoiceRepository.save(invoice);
        return enrollment;
    }

    @Test
    void unAlumnoSinDeudaPuedeMatricularseSinProblema() {
        Student student = persistStudent("88010001");
        Program program = persistPrograma();

        var enrollment = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void alumnoConDeudaOverdueEnUnaCarreraNoPuedeMatricularseEnOtraCarreraDistinta() {
        Student student = persistStudent("88010002");
        Program programConDeuda = persistPrograma();
        persistOldWithdrawnCycle(student, programConDeuda, BigDecimal.valueOf(5000));
        Program programNuevo = persistPrograma();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId())))
                .isInstanceOf(StudentInDebtException.class);
    }

    @Test
    void unaInvoicePendingDentroDeLaGraciaNoBloqueaUnaAltaNueva() {
        Student student = persistStudent("88010003");
        Program programConCuotaPendiente = persistPrograma();
        // PENDING (todavía no OVERDUE) — no debería bloquear.
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(programConCuotaPendiente);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment = programEnrollmentRepository.save(enrollment);
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now().plusDays(5));
        invoice.setBaseAmount(BigDecimal.valueOf(5000));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoiceRepository.save(invoice);

        Program programNuevo = persistPrograma();
        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId()));

        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void saldarCompletoHabilitaLaReMatriculacion() {
        Person admin = persistPerson("admin.debt1." + System.nanoTime());
        Student student = persistStudent("88010004");
        Program program = persistPrograma();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));
        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);

        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(deudaVieja.getId(), BigDecimal.valueOf(5000))),
                PaymentMethod.CASH, false, null, null, null, null), admin);

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void saldarParcialSigueBloqueando() {
        Person admin = persistPerson("admin.debt2." + System.nanoTime());
        Student student = persistStudent("88010005");
        Program program = persistPrograma();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));
        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);

        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(deudaVieja.getId(), BigDecimal.valueOf(2000))),
                PaymentMethod.CASH, false, null, null, null, null), admin);

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(StudentInDebtException.class);
    }

    @Test
    void condonarConRolDirectorLiberaLaDeudaSinPagoYPermiteMatricularDeInmediato() {
        Person director = persistPerson("director.debt3." + System.nanoTime());
        Student student = persistStudent("88010006");
        Program program = persistPrograma();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));

        studentDebtResolutionService.waiveDebtForProgram(student.getId(), program.getId(), "Condonado por Director", director);

        Invoice deudaVieja = invoiceRepository.findByIdWithAdjustments(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0).getId()).orElseThrow();
        assertThat(deudaVieja.getStatus()).isEqualTo(InvoiceStatus.WAIVED);

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));
        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    // Test 68: una Scholarship vive atada al enrollment sobre el que se otorgó, no se traslada
    // sola al ciclo nuevo al reincorporar.
    @Test
    void becaDelCicloViejoNoSeArrastraAutomaticamenteAlReincorporar() {
        Person director = persistPerson("director.debt6." + System.nanoTime());
        Student student = persistStudent("88010010");
        Program program = persistPrograma();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));

        com.red_escuela.backend.billing.entity.Scholarship scholarship = new com.red_escuela.backend.billing.entity.Scholarship();
        scholarship.setProgramEnrollment(cicloViejo);
        scholarship.setValue(new com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue.FixedAmount(BigDecimal.valueOf(2000)));
        scholarship.setReason("Beca del ciclo viejo");
        scholarship.setGrantedBy(director);
        scholarship.addInstallments(3);
        scholarshipRepository.save(scholarship);

        studentDebtResolutionService.waiveDebtForProgram(student.getId(), program.getId(), "Condonado por Director", director);
        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(scholarshipRepository.findByProgramEnrollmentId(nuevaMatricula.getId())).isEmpty();
        assertThat(scholarshipRepository.findByProgramEnrollmentId(cicloViejo.getId())).hasSize(1);
    }

    @Test
    void multiplesCiclosViejosConDeudaDebenResolverseTodos() {
        Person director = persistPerson("director.debt4." + System.nanoTime());
        Student student = persistStudent("88010007");
        Program program = persistPrograma();
        ProgramEnrollment ciclo1 = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(3000));
        ProgramEnrollment ciclo2 = persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(4000));

        studentDebtResolutionService.waiveDebtForProgram(student.getId(), program.getId(), "Condonado por Director", director);

        Invoice deuda1 = invoiceRepository.findByIdWithAdjustments(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(ciclo1.getId()).get(0).getId()).orElseThrow();
        Invoice deuda2 = invoiceRepository.findByIdWithAdjustments(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(ciclo2.getId()).get(0).getId()).orElseThrow();
        assertThat(deuda1.getStatus()).isEqualTo(InvoiceStatus.WAIVED);
        assertThat(deuda2.getStatus()).isEqualTo(InvoiceStatus.WAIVED);

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));
        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    // Test 120: la deuda no tiene que resolverse en el enrollment nuevo que se está por crear —
    // saldarla en CUALQUIER otro enrollment del alumno ya desbloquea el alta.
    @Test
    void saldarLaDeudaEnCualquierEnrollmentDesbloqueaAltasEnCualquierOtro() {
        Person admin = persistPerson("admin.debt5." + System.nanoTime());
        Student student = persistStudent("88010009");
        Program programConDeudaVieja = persistPrograma();
        ProgramEnrollment cicloViejo = persistOldWithdrawnCycle(student, programConDeudaVieja, BigDecimal.valueOf(5000));
        Program programNuevo = persistPrograma();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId())))
                .isInstanceOf(StudentInDebtException.class);

        Invoice deudaVieja = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(cicloViejo.getId()).get(0);
        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(deudaVieja.getId(), BigDecimal.valueOf(5000))),
                PaymentMethod.CASH, false, null, null, null, null), admin);

        var nuevaMatricula = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(programNuevo.getId()));
        assertThat(nuevaMatricula.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    // Test 49: bypass directo del endpoint de matriculación (sin pasar por
    // StudentDebtResolutionService.reincorporar) sigue bloqueado — matricular() ya tiene el hook.
    @Test
    void bypassDirectoDeMatricularSigueBloqueadoConDeudaVieja() {
        Student student = persistStudent("88010008");
        Program program = persistPrograma();
        persistOldWithdrawnCycle(student, program, BigDecimal.valueOf(5000));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(StudentInDebtException.class);
    }
}
