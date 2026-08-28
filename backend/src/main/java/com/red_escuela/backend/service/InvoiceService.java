package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateInvoiceFromFeeScheduleRequest;
import com.red_escuela.backend.dto.CreateInvoiceRequest;
import com.red_escuela.backend.dto.DebtSummaryResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.Discount;
import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.LateFeePolicy;
import com.red_escuela.backend.entity.PaymentStanding;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.Promotion;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.InvalidPricingTargetException;
import com.red_escuela.backend.exception.InvoiceAlreadyCancelledException;
import com.red_escuela.backend.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-AL-09/RF-AD-08: facturación interna sin pasarela (decisión 1) — el estado de deuda de un
 * alumno nunca es un campo persistido, siempre se calcula on-demand a partir de Invoice/Payment
 * (decisión 3), reemplazando el rol que cumplía el campo muerto Student.defaulter.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final FeeScheduleService feeScheduleService;
    private final DiscountService discountService;
    private final PromotionService promotionService;
    private final LateFeePolicyService lateFeePolicyService;

    public InvoiceService(
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository, StudentRepository studentRepository,
            ProgramRepository programRepository, CourseRepository courseRepository, FeeScheduleService feeScheduleService,
            DiscountService discountService, PromotionService promotionService, LateFeePolicyService lateFeePolicyService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.feeScheduleService = feeScheduleService;
        this.discountService = discountService;
        this.promotionService = promotionService;
        this.lateFeePolicyService = lateFeePolicyService;
    }

    @Transactional
    public Invoice crear(Long studentId, CreateInvoiceRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        Invoice invoice = new Invoice();
        invoice.setStudent(student);
        invoice.setConcept(request.concept());
        invoice.setAmount(request.amount());
        invoice.setDueDate(request.dueDate());
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    /**
     * Genera la factura resolviendo el precio vigente del {@link FeeSchedule}
     * (matrícula o cuota, según {@code concept} — ver decisión de negocio) más
     * los descuentos/promociones vigentes para ese alumno y esa carrera/curso, en
     * vez de un monto libre tipeado a mano ({@link #crear} sigue existiendo para
     * facturación ad-hoc, no se elimina). El monto queda congelado en la factura
     * al momento de emitirla — un cambio de precio o de descuento después no la
     * afecta retroactivamente (mismo criterio que {@link #crear}).
     */
    @Transactional
    public Invoice crearDesdeFeeSchedule(Long studentId, CreateInvoiceFromFeeScheduleRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        boolean tienePrograma = request.programId() != null;
        boolean tieneCurso = request.courseId() != null;
        if (tienePrograma == tieneCurso) {
            throw new InvalidPricingTargetException(
                    "Hay que indicar exactamente una carrera o un curso, no los dos ni ninguno");
        }

        LocalDate hoy = LocalDate.now();
        String conceptoTexto;
        FeeSchedule feeSchedule;
        List<Promotion> promocionesVigentes;
        if (tienePrograma) {
            Program program = programRepository.findById(request.programId())
                    .orElseThrow(() -> new ProgramNotFoundException(request.programId()));
            feeSchedule = feeScheduleService.vigenteParaPrograma(program.getId(), request.concept(), hoy);
            promocionesVigentes = promotionService.vigentesParaPrograma(program.getId(), hoy);
            conceptoTexto = (request.concept() == FeeScheduleConcept.MATRICULA
                    ? "Matrícula — " : "Cuota — ") + program.getName();
        } else {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
            feeSchedule = feeScheduleService.vigenteParaCurso(course.getId(), request.concept(), hoy);
            promocionesVigentes = promotionService.vigentesParaCurso(course.getId(), hoy);
            conceptoTexto = "Cuota — " + course.getName();
        }

        List<Discount> descuentosVigentes = discountService.vigentesParaAlumno(studentId, hoy);
        BigDecimal montoFinal = PricingCalculator.calcularMontoFinal(feeSchedule.getAmount(), descuentosVigentes, promocionesVigentes);

        Invoice invoice = new Invoice();
        invoice.setStudent(student);
        invoice.setConcept(conceptoTexto);
        invoice.setAmount(montoFinal);
        invoice.setDueDate(request.dueDate());
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    // Recargo informativo — no bloquea ni se exige en el pago (decisión de
    // negocio: nunca corte automático, siempre calculado on-demand, nunca
    // persistido). Cero si no hay política configurada o la factura no está en
    // mora, mismo criterio que el resto del cálculo de deuda de esta clase.
    public BigDecimal calcularRecargoPorMora(Invoice invoice) {
        return lateFeePolicyService.vigenteEl(LocalDate.now())
                .map(policy -> LateFeeCalculator.calcularRecargo(policy, invoice.getDueDate(), LocalDate.now(), calcularSaldoPendiente(invoice)))
                .orElse(BigDecimal.ZERO);
    }

    public boolean estaEnRiesgoDeCorte(Invoice invoice) {
        return lateFeePolicyService.vigenteEl(LocalDate.now())
                .map(policy -> LateFeeCalculator.estaEnRiesgoDeCorte(policy, invoice.getDueDate(), LocalDate.now()))
                .orElse(false);
    }

    public List<InvoiceResponse> listarPorAlumno(Long studentId) {
        return invoiceRepository.findByStudentId(studentId).stream()
                .map(invoice -> InvoiceResponse.from(invoice, calcularSaldoPendiente(invoice)))
                .toList();
    }

    @Transactional
    public void cancelar(Long invoiceId) {
        Invoice invoice = findOrThrow(invoiceId);
        // No se puede cancelar una factura ya saldada (PAID) ni una ya CANCELLED.
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvoiceAlreadyCancelledException(invoiceId);
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    // Decisión 3: siempre calculado on-demand, nunca un campo persistido — una factura recién
    // pagada se refleja inmediatamente, sin job ni sincronización posterior.
    public DebtSummaryResponse calcularDeuda(Long studentId) {
        List<Invoice> pendientes = invoiceRepository.findByStudentIdAndStatusIn(
                studentId, List.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE));

        BigDecimal montoAdeudado = pendientes.stream()
                .map(this::calcularSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InvoiceResponse> facturasPendientes = pendientes.stream()
                .map(invoice -> InvoiceResponse.from(invoice, calcularSaldoPendiente(invoice)))
                .toList();

        return new DebtSummaryResponse(montoAdeudado.compareTo(BigDecimal.ZERO) > 0, montoAdeudado, facturasPendientes);
    }

    /**
     * Decisión 17 — punto de extensión a futuro para una validación de inscripción, sin ningún
     * caller de bloqueo todavía (ver Deuda técnica de la spec). OVERDUE hoy no lo setea ningún
     * job (ver Deuda técnica): una factura PENDING/PARTIALLY_PAID con dueDate ya pasada cuenta
     * como vencida acá mismo, on-demand, sin necesitar que la base ya la tenga en OVERDUE.
     */
    public PaymentStanding calcularEstadoDePago(Long studentId) {
        List<Invoice> todasLasFacturas = invoiceRepository.findByStudentId(studentId);
        if (todasLasFacturas.isEmpty()) {
            return PaymentStanding.SIN_INFORMACION;
        }
        LocalDate hoy = LocalDate.now();
        boolean hayVencidaImpaga = todasLasFacturas.stream().anyMatch(invoice ->
                invoice.getStatus() == InvoiceStatus.OVERDUE
                        || ((invoice.getStatus() == InvoiceStatus.PENDING || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                                && invoice.getDueDate().isBefore(hoy)));
        return hayVencidaImpaga ? PaymentStanding.MOROSO : PaymentStanding.AL_DIA;
    }

    BigDecimal calcularSaldoPendiente(Invoice invoice) {
        BigDecimal pagado = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoice.getAmount().subtract(pagado);
    }

    Invoice findOrThrow(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }
}
