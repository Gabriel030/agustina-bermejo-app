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
        return priceRevisionRepository.findTopByCourseIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(courseId, feeType, at)
                .map(PriceRevision::getMonthlyAmount)
                .orElseThrow(() -> new PriceRevisionNotFoundException("No PriceRevision found for course " + courseId + "/" + feeType));
    }

    public BigDecimal resolveCurrentForProgram(Long programId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByProgramIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(programId, feeType, at)
                .map(PriceRevision::getMonthlyAmount)
                .orElseThrow(() -> new PriceRevisionNotFoundException("No PriceRevision found for program " + programId + "/" + feeType));
    }
}
