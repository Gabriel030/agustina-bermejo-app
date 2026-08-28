package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateFeeScheduleRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.FeeScheduleNotFoundException;
import com.red_escuela.backend.exception.InvalidPricingTargetException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.FeeScheduleRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public FeeSchedule crear(CreateFeeScheduleRequest request, Person createdBy) {
        boolean tieneProgram = request.programId() != null;
        boolean tieneCourse = request.courseId() != null;
        if (tieneProgram == tieneCourse) {
            throw new InvalidPricingTargetException(
                    "Un FeeSchedule tiene que referenciar exactamente una carrera o un curso, no los dos ni ninguno");
        }
        // Un curso nunca tiene matrícula — es la regla de negocio central de esta
        // entidad (ver decisión "para un curso, la primera cuota es la matrícula").
        if (tieneCourse && request.concept() == FeeScheduleConcept.MATRICULA) {
            throw new InvalidPricingTargetException("Un curso no tiene matrícula, solo cuota mensual");
        }

        FeeSchedule feeSchedule = new FeeSchedule();
        if (tieneProgram) {
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
        feeSchedule.setVigenteDesde(request.vigenteDesde());
        feeSchedule.setCreatedBy(createdBy);
        feeSchedule.setCreatedAt(LocalDateTime.now());
        return feeScheduleRepository.save(feeSchedule);
    }

    public List<FeeSchedule> listar() {
        return feeScheduleRepository.findAllByOrderByVigenteDesdeDesc();
    }

    public FeeSchedule vigenteParaPrograma(Long programId, FeeScheduleConcept concept, LocalDate fecha) {
        return feeScheduleRepository
                .findFirstByProgramIdAndConceptAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(programId, concept, fecha)
                .orElseThrow(() -> new FeeScheduleNotFoundException("program " + programId + ", concepto " + concept));
    }

    public FeeSchedule vigenteParaCurso(Long courseId, FeeScheduleConcept concept, LocalDate fecha) {
        return feeScheduleRepository
                .findFirstByCourseIdAndConceptAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(courseId, concept, fecha)
                .orElseThrow(() -> new FeeScheduleNotFoundException("course " + courseId + ", concepto " + concept));
    }
}
