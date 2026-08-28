package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateDiscountRequest;
import com.red_escuela.backend.dto.CreateFeeScheduleRequest;
import com.red_escuela.backend.dto.CreateInvoiceFromFeeScheduleRequest;
import com.red_escuela.backend.dto.CreatePromotionRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.DiscountType;
import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.FeeScheduleNotFoundException;
import com.red_escuela.backend.exception.InvalidPricingTargetException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Épica 8 Fase 1: versionado de FeeSchedule por fila nueva (nunca se pisa un
 * precio anterior), descuentos individuales + promociones segmentadas sumados
 * al generar una factura, y las validaciones de forma (XOR programId/courseId).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PricingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FeeScheduleService feeScheduleService;

    @Autowired
    private DiscountService discountService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private EntityManager entityManager;

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

    private Student persistStudent(String dni) {
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistProgram(String nombre) {
        Program program = new Program();
        program.setName(nombre);
        program.setStatus(StatusType.ACTIVE);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    private Course persistCourse(String nombre) {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Periodo QA " + System.nanoTime());
        term.setType("ANUAL");
        entityManager.persist(term);

        Course course = new Course();
        course.setName(nombre);
        course.setTerm(term);
        entityManager.persist(course);
        return course;
    }

    @Test
    void feeScheduleRequiereExactamenteUnoDeProgramaOCurso() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        entityManager.flush();

        assertThatThrownBy(() -> feeScheduleService.crear(
                new CreateFeeScheduleRequest(null, null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(1000), LocalDate.now()),
                director))
                .isInstanceOf(InvalidPricingTargetException.class);
    }

    @Test
    void unCursoNoPuedeTenerMatricula() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        Course course = persistCourse("Curso QA " + System.nanoTime());
        entityManager.flush();

        assertThatThrownBy(() -> feeScheduleService.crear(
                new CreateFeeScheduleRequest(null, course.getId(), FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(1000), LocalDate.now()),
                director))
                .isInstanceOf(InvalidPricingTargetException.class);
    }

    @Test
    void unPrecioNuevoNoAfectaFacturasYaEmitidasElVigenteEsElMasRecienteHastaLaFecha() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        Program program = persistProgram("Carrera QA " + System.nanoTime());
        entityManager.flush();

        feeScheduleService.crear(new CreateFeeScheduleRequest(
                program.getId(), null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(50000), LocalDate.of(2026, 1, 1)), director);
        feeScheduleService.crear(new CreateFeeScheduleRequest(
                program.getId(), null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(60000), LocalDate.of(2026, 4, 1)), director);

        FeeSchedule vigenteEnMarzo = feeScheduleService.vigenteParaPrograma(
                program.getId(), FeeScheduleConcept.MATRICULA, LocalDate.of(2026, 3, 1));
        assertThat(vigenteEnMarzo.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        FeeSchedule vigenteEnMayo = feeScheduleService.vigenteParaPrograma(
                program.getId(), FeeScheduleConcept.MATRICULA, LocalDate.of(2026, 5, 1));
        assertThat(vigenteEnMayo.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(60000));
    }

    @Test
    void sinFeeScheduleVigenteFallaAlIntentarGenerarLaFactura() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        Program program = persistProgram("Carrera sin precio " + System.nanoTime());
        Student student = persistStudent("79910001");
        entityManager.flush();

        assertThatThrownBy(() -> invoiceService.crearDesdeFeeSchedule(
                student.getId(),
                new CreateInvoiceFromFeeScheduleRequest(program.getId(), null, FeeScheduleConcept.MATRICULA, LocalDate.now().plusDays(10))))
                .isInstanceOf(FeeScheduleNotFoundException.class);
    }

    @Test
    void generarFacturaDeMatriculaResuelveElMontoDelFeeScheduleVigente() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        Program program = persistProgram("Carrera con precio " + System.nanoTime());
        Student student = persistStudent("79910002");
        feeScheduleService.crear(new CreateFeeScheduleRequest(
                program.getId(), null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(50000), LocalDate.now().minusDays(1)), director);
        entityManager.flush();

        Invoice invoice = invoiceService.crearDesdeFeeSchedule(
                student.getId(),
                new CreateInvoiceFromFeeScheduleRequest(program.getId(), null, FeeScheduleConcept.MATRICULA, LocalDate.now().plusDays(10)));

        assertThat(invoice.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(invoice.getConcept()).contains("Matrícula");
    }

    @Test
    void descuentoIndividualYPromocionSegmentadaSeSumanAlGenerarLaFactura() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        Program program = persistProgram("Carrera con descuentos " + System.nanoTime());
        Student student = persistStudent("79910003");
        feeScheduleService.crear(new CreateFeeScheduleRequest(
                program.getId(), null, FeeScheduleConcept.CUOTA_MENSUAL, BigDecimal.valueOf(10000), LocalDate.now().minusDays(1)), director);
        discountService.crear(student.getId(), new CreateDiscountRequest(
                DiscountType.PORCENTAJE, BigDecimal.valueOf(20), LocalDate.now().minusDays(1), null, "Beca por mérito académico"), director);
        promotionService.crear(new CreatePromotionRequest(
                program.getId(), null, DiscountType.PORCENTAJE, BigDecimal.valueOf(10), LocalDate.now().minusDays(1), null), director);
        entityManager.flush();

        Invoice invoice = invoiceService.crearDesdeFeeSchedule(
                student.getId(),
                new CreateInvoiceFromFeeScheduleRequest(program.getId(), null, FeeScheduleConcept.CUOTA_MENSUAL, LocalDate.now().plusDays(10)));

        // 20% (individual) + 10% (segmentada) = 30% de descuento total sobre 10000 → 7000.
        assertThat(invoice.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000));
    }

    @Test
    void unaPromocionDeOtraCarreraNoAplica() {
        Person director = persistPersonBase("DIR" + System.nanoTime());
        Program programA = persistProgram("Carrera A " + System.nanoTime());
        Program programB = persistProgram("Carrera B " + System.nanoTime());
        Student student = persistStudent("79910004");
        feeScheduleService.crear(new CreateFeeScheduleRequest(
                programA.getId(), null, FeeScheduleConcept.CUOTA_MENSUAL, BigDecimal.valueOf(10000), LocalDate.now().minusDays(1)), director);
        promotionService.crear(new CreatePromotionRequest(
                programB.getId(), null, DiscountType.PORCENTAJE, BigDecimal.valueOf(50), LocalDate.now().minusDays(1), null), director);
        entityManager.flush();

        Invoice invoice = invoiceService.crearDesdeFeeSchedule(
                student.getId(),
                new CreateInvoiceFromFeeScheduleRequest(programA.getId(), null, FeeScheduleConcept.CUOTA_MENSUAL, LocalDate.now().plusDays(10)));

        assertThat(invoice.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }
}
