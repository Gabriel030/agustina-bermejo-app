package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PriceRevision;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.repository.PriceRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

// Value Object with temporal validity — hooked from ProgramService/CourseService.crear/editar
// (Fase 6.1, "Impacto en código existente"): without this, a freshly created Course/Program
// would have no PriceRevision and InvoiceFactory could not resolve any price for its first
// installment.
@Service
public class PriceRevisionService {

    private final PriceRevisionRepository priceRevisionRepository;

    public PriceRevisionService(PriceRevisionRepository priceRevisionRepository) {
        this.priceRevisionRepository = priceRevisionRepository;
    }

    @Transactional
    public PriceRevision registerForCourse(Course course, FeeType feeType, BigDecimal monthlyAmount, String description, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setCourse(course);
        revision.setFeeType(feeType);
        revision.setMonthlyAmount(monthlyAmount);
        revision.setDescription(description);
        revision.setEffectiveFrom(LocalDate.now());
        revision.setCreatedBy(createdBy);
        return priceRevisionRepository.save(revision);
    }

    @Transactional
    public PriceRevision registerForProgram(Program program, FeeType feeType, BigDecimal monthlyAmount, String description, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setFeeType(feeType);
        revision.setMonthlyAmount(monthlyAmount);
        revision.setDescription(description);
        revision.setEffectiveFrom(LocalDate.now());
        revision.setCreatedBy(createdBy);
        return priceRevisionRepository.save(revision);
    }

    public BigDecimal resolveCurrentForCourse(Long courseId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByCourseIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(courseId, feeType, at)
                .map(PriceRevision::getMonthlyAmount)
                .orElseThrow(() -> new PriceRevisionNotFoundException("No PriceRevision found for course " + courseId + "/" + feeType));
    }

    public BigDecimal resolveCurrentForProgram(Long programId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByProgramIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(programId, feeType, at)
                .map(PriceRevision::getMonthlyAmount)
                .orElseThrow(() -> new PriceRevisionNotFoundException("No PriceRevision found for program " + programId + "/" + feeType));
    }

    // Usado por InvoiceQueryService.priceBreakdown ("transparencia de precio", Flujo 16) para
    // mostrar la descripción del PriceRevision resuelto — best-effort no-throw, a diferencia de
    // resolveCurrentFor*: acá no encontrar nada no es un error, solo significa que esa línea del
    // desglose queda sin descripción (hallazgo de auditoría 2026-09-04, campo faltante).
    public Optional<PriceRevision> findRevisionForCourse(Long courseId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByCourseIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(courseId, feeType, at);
    }

    public Optional<PriceRevision> findRevisionForProgram(Long programId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByProgramIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(programId, feeType, at);
    }
}
