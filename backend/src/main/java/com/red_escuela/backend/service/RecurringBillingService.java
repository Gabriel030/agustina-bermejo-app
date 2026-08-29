package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateInvoiceFromFeeScheduleRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Épica 8 Fase 2: genera automáticamente la Invoice de cuota mensual de cada
 * matriculación activa, sin que Administración las cree a mano. No es una cola
 * de mensajes ni tiene estado propio entre corridas — cada vez que corre le
 * pregunta a la base "¿qué cuota corresponde hoy que todavía no existe?" y la
 * crea; eso es lo que lo hace seguro ante un reinicio/deploy a mitad de camino
 * (ver spec técnica, "Verificación": nada se pierde porque nunca hubo nada en
 * memoria para perder, y la idempotencia por constraint único evita duplicar
 * si se reintenta).
 */
@Service
public class RecurringBillingService {

    private static final Logger log = LoggerFactory.getLogger(RecurringBillingService.class);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;

    public RecurringBillingService(
            ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
            InvoiceRepository invoiceRepository, InvoiceService invoiceService) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
    }

    // Corre todos los días, no solo el 1° — si el backend estuvo caído la noche
    // del corte mensual, la corrida del día siguiente igual detecta y genera la
    // cuota atrasada (ver spec técnica, decisión 7). Mismo método que dispara
    // POST /billing/run — sin lógica duplicada entre cron y disparo manual.
    @Scheduled(cron = "0 0 3 * * *")
    public void generarCuotasDelDia() {
        LocalDate hoy = LocalDate.now();
        generarCuotasDeCarreras(hoy);
        generarCuotasDeCursos(hoy);
    }

    void generarCuotasDeCarreras(LocalDate hoy) {
        List<ProgramEnrollment> activas = programEnrollmentRepository.findByStatus(ProgramEnrollmentStatus.ENROLLED);
        int generadas = 0;
        for (ProgramEnrollment enrollment : activas) {
            try {
                if (procesarCarrera(enrollment, hoy)) {
                    generadas++;
                }
            } catch (Exception e) {
                // Un alumno con datos mal cargados no puede frenar la facturación de
                // todos los demás — se loguea y se sigue con el resto de la corrida.
                log.error("Motor de facturación: error generando cuota para ProgramEnrollment {}", enrollment.getId(), e);
            }
        }
        log.info("Motor de facturación (carreras): {} matriculaciones revisadas, {} cuotas generadas", activas.size(), generadas);
    }

    void generarCuotasDeCursos(LocalDate hoy) {
        List<CourseEnrollment> activas = courseEnrollmentRepository.findByStatus(EnrollmentStatus.ENROLLED);
        int generadas = 0;
        for (CourseEnrollment enrollment : activas) {
            try {
                if (procesarCurso(enrollment, hoy)) {
                    generadas++;
                }
            } catch (Exception e) {
                log.error("Motor de facturación: error generando cuota para CourseEnrollment {}", enrollment.getId(), e);
            }
        }
        log.info("Motor de facturación (cursos): {} matriculaciones revisadas, {} cuotas generadas", activas.size(), generadas);
    }

    @Transactional
    boolean procesarCarrera(ProgramEnrollment enrollment, LocalDate hoy) {
        ProgramCohort cohort = enrollment.getProgramCohort();
        Program program = enrollment.getProgram();
        // Alta directa por staff sin cohorte, o carrera sin plan de pagos configurado
        // (totalYears/cuotasPorAnioLectivo): fuera del alcance del motor, mismo
        // criterio que un curso sin FeeSchedule — no hay de dónde derivar el plan.
        if (cohort == null || program.getTotalYears() == null || program.getCuotasPorAnioLectivo() == null) {
            return false;
        }

        int totalCuotas = program.getTotalYears() * program.getCuotasPorAnioLectivo();
        Integer numeroCuota = InstallmentScheduleCalculator.calcularNumeroDeCuota(cohort.getCursadaStart(), totalCuotas, hoy);
        if (numeroCuota == null) {
            return false;
        }
        if (invoiceRepository.existsByProgramEnrollmentIdAndInstallmentNumber(enrollment.getId(), numeroCuota)) {
            return false;
        }

        LocalDate dueDate = cohort.getCursadaStart().plusMonths(numeroCuota - 1L).withDayOfMonth(1);
        invoiceService.crearDesdeFeeSchedule(
                enrollment.getStudent().getId(),
                new CreateInvoiceFromFeeScheduleRequest(program.getId(), null, FeeScheduleConcept.CUOTA_MENSUAL, dueDate),
                null, enrollment, null, numeroCuota, totalCuotas);
        return true;
    }

    @Transactional
    boolean procesarCurso(CourseEnrollment enrollment, LocalDate hoy) {
        Course course = enrollment.getCourse();
        if (course.getDurationMonths() == null || course.getTerm() == null || course.getTerm().getStartDate() == null) {
            return false;
        }

        LocalDate cursadaStart = course.getTerm().getStartDate().toLocalDate();
        Integer numeroCuota = InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, course.getDurationMonths(), hoy);
        if (numeroCuota == null) {
            return false;
        }
        if (invoiceRepository.existsByCourseEnrollmentIdAndInstallmentNumber(enrollment.getId(), numeroCuota)) {
            return false;
        }

        LocalDate dueDate = cursadaStart.plusMonths(numeroCuota - 1L).withDayOfMonth(1);
        invoiceService.crearDesdeFeeSchedule(
                enrollment.getStudent().getId(),
                new CreateInvoiceFromFeeScheduleRequest(null, course.getId(), FeeScheduleConcept.CUOTA_MENSUAL, dueDate),
                null, null, enrollment, numeroCuota, course.getDurationMonths());
        return true;
    }
}
