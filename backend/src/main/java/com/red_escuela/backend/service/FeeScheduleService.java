package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateFeeScheduleRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.FeeScheduleNotFoundException;
import com.red_escuela.backend.exception.InvalidPricingTargetException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.FeeScheduleRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Precio de matrícula/cuota mensual de una carrera o curso, versionado por fila
 * nueva (nunca se pisa un precio anterior — ver Invoice, que congela el monto al
 * emitirse). Solo el Director define precios (regla de negocio explícita).
 */
@Service
public class FeeScheduleService {

    private final FeeScheduleRepository feeScheduleRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    public FeeScheduleService(
            FeeScheduleRepository feeScheduleRepository, ProgramRepository programRepository,
            CourseRepository courseRepository) {
        this.feeScheduleRepository = feeScheduleRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public FeeSchedule create(CreateFeeScheduleRequest request, Person createdBy) {
        boolean hasProgram = request.programId() != null;
        boolean hasCourse = request.courseId() != null;
        if (hasProgram == hasCourse) {
            throw new InvalidPricingTargetException(
                    "Un FeeSchedule tiene que referenciar exactamente una carrera o un curso, no los dos ni ninguno");
        }
        // Un curso nunca tiene matrícula — es la regla de negocio central de esta
        // entidad (ver decisión "para un curso, la primera cuota es la matrícula").
        if (hasCourse && request.concept() == FeeScheduleConcept.MATRICULA) {
            throw new InvalidPricingTargetException("Un curso no tiene matrícula, solo cuota mensual");
        }

        FeeSchedule feeSchedule = new FeeSchedule();
        if (hasProgram) {
            Program program = programRepository.findById(request.programId())
                    .orElseThrow(() -> new ProgramNotFoundException(request.programId()));
            feeSchedule.setProgram(program);
        } else {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
            feeSchedule.setCourse(course);
        }
        feeSchedule.setConcept(request.concept());
        feeSchedule.setAmount(request.amount());
        feeSchedule.setValidFrom(request.vigenteDesde());
        feeSchedule.setCreatedBy(createdBy);
        feeSchedule.setCreatedAt(LocalDateTime.now());
        return feeScheduleRepository.save(feeSchedule);
    }

    public List<FeeSchedule> list() {
        return feeScheduleRepository.findAllByOrderByValidFromDesc();
    }

    public FeeSchedule validForProgram(Long programId, FeeScheduleConcept concept, LocalDate date) {
        return feeScheduleRepository
                .findFirstByProgramIdAndConceptAndValidFromLessThanEqualOrderByValidFromDesc(programId, concept, date)
                .orElseThrow(() -> new FeeScheduleNotFoundException("program " + programId + ", concepto " + concept));
    }

    public FeeSchedule validForCourse(Long courseId, FeeScheduleConcept concept, LocalDate date) {
        return feeScheduleRepository
                .findFirstByCourseIdAndConceptAndValidFromLessThanEqualOrderByValidFromDesc(courseId, concept, date)
                .orElseThrow(() -> new FeeScheduleNotFoundException("course " + courseId + ", concepto " + concept));
    }

    /**
     * Épica 7 Fase 3: matrícula vigente ya ajustada por cohorte (ver
     * {@link PricingCalculator#calculateEnrollmentFeeAmount}, Épica 8 Fase 2 — 50%
     * para la segunda cohorte del año). Cualquier alumno tiene que poder ver
     * este monto ANTES de transferir, no solo Director/Administrativo — por
     * eso es un método público separado, sin las restricciones del resto de
     * esta clase (que sí es Director-only para create/editar).
     * {@code ordenEnAnioCohorte} nullable: sin cohorte elegida todavía
     * (ej. la pantalla de Detalle, antes de arrancar el wizard), devuelve el
     * monto base sin prorratear.
     */
    public BigDecimal currentEnrollmentFeeAmount(Long programId, Integer orderInCohortYear, LocalDate date) {
        FeeSchedule feeSchedule = validForProgram(programId, FeeScheduleConcept.MATRICULA, date);
        if (orderInCohortYear == null) {
            return feeSchedule.getAmount();
        }
        ProgramCohort transientCohort = new ProgramCohort();
        transientCohort.setOrderInYear(orderInCohortYear);
        return PricingCalculator.calculateEnrollmentFeeAmount(feeSchedule.getAmount(), transientCohort);
    }
}
