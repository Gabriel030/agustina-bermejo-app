package com.red_escuela.backend.service;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

// Orquestador fino (patrón táctico DDD, Domain Service): la INVARIANTE de negocio — "¿puedo
// facturar esta cuota?" — vive en ProgramYearBillingTemplate.canGenerateInvoice y
// Course.canGenerateInvoice (cada una se valida a sí misma dado un conteo). Este service
// existe solo porque esa validación necesita datos que las entidades no pueden buscar por sí
// solas (repositorios, y una instancia managed para evitar detached-entity lazy loading) —
// PaymentPlanSelectionService e InvoiceGenerationService comparten esa orquestación, no la
// regla en sí (decisión 2026-09-03).
@Service
public class RecurringFeeWindowService {

    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public RecurringFeeWindowService(InvoiceRepository invoiceRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                        CourseEnrollmentRepository courseEnrollmentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public boolean canBeBilled(ProgramEnrollment programEnrollment, YearMonth period) {
        ProgramYearBillingTemplate template = currentTemplate(programEnrollment);
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId()), template.getTerm());
        return template.canGenerateInvoice(period, generated);
    }

    public int remainingInvoices(ProgramEnrollment programEnrollment) {
        ProgramYearBillingTemplate template = currentTemplate(programEnrollment);
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId()), template.getTerm());
        return template.remainingInvoices(generated);
    }

    public boolean canBeBilled(CourseEnrollment courseEnrollment, YearMonth period) {
        var course = managedCourseEnrollment(courseEnrollment).getCourse();
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId()), course.getTerm());
        return course.canGenerateInvoice(period, generated);
    }

    public Integer remainingInvoices(CourseEnrollment courseEnrollment) {
        var course = managedCourseEnrollment(courseEnrollment).getCourse();
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId()), course.getTerm());
        return course.remainingInvoices(generated);
    }

    // La ProgramEnrollment/CourseEnrollment que recibe cada método público puede llegar
    // detached (ej. leída por un caller en otra transacción, como hará el futuro cron mensual) —
    // navegar currentBillingTemplate/programCohort/course sobre esa instancia directamente
    // revienta con LazyInitializationException apenas se toca una relación @ManyToOne LAZY sin
    // sesión activa. Se re-busca acá, siempre dentro de la transacción de ESTE servicio, para
    // garantizar una instancia managed antes de navegar cualquier relación lazy.
    private ProgramYearBillingTemplate currentTemplate(ProgramEnrollment programEnrollment) {
        ProgramEnrollment managed = programEnrollmentRepository.findById(programEnrollment.getId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollment.getId()));
        return managed.getCurrentBillingTemplate() != null
                ? managed.getCurrentBillingTemplate()
                : managed.getProgramCohort().getTemplate();
    }

    private CourseEnrollment managedCourseEnrollment(CourseEnrollment courseEnrollment) {
        return courseEnrollmentRepository.findById(courseEnrollment.getId())
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollment.getId()));
    }

    // Filtrar por FeeType/período es una consulta, no una invariante de dominio — se apoya en
    // Term.contains (la invariante real) para no reimplementar la misma comparación de fechas.
    private long recurringFeeInvoicesGeneratedInWindow(List<Invoice> invoices, Term term) {
        return invoices.stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)
                .filter(i -> term.contains(i.getPeriod()))
                .count();
    }
}
