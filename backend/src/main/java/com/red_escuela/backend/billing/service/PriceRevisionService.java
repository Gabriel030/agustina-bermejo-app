package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class PriceRevisionService {
    private final PriceRevisionRepository priceRevisionRepository;

    public PriceRevisionService(PriceRevisionRepository priceRevisionRepository) {
        this.priceRevisionRepository = priceRevisionRepository;
    }

    @Transactional
    public PriceRevision registerForCourse(Course course, FeeType feeType, Money monthlyAmount, String description, Person createdBy) {
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
    public PriceRevision registerForProgram(Program program, FeeType feeType, Money monthlyAmount, String description, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setFeeType(feeType);
        revision.setMonthlyAmount(monthlyAmount);
        revision.setDescription(description);
        revision.setEffectiveFrom(LocalDate.now());
        revision.setCreatedBy(createdBy);
        return priceRevisionRepository.save(revision);
    }

    public Money resolveCurrentForCourse(Long courseId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByCourseIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(courseId, feeType, at)
                .map(PriceRevision::getMonthlyAmount)
                .orElseThrow(() -> new PriceRevisionNotFoundException("No PriceRevision found for course " + courseId + "/" + feeType));
    }

    public Money resolveCurrentForProgram(Long programId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByProgramIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(programId, feeType, at)
                .map(PriceRevision::getMonthlyAmount)
                .orElseThrow(() -> new PriceRevisionNotFoundException("No PriceRevision found for program " + programId + "/" + feeType));
    }

    public Optional<PriceRevision> findRevisionForCourse(Long courseId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByCourseIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(courseId, feeType, at);
    }

    public Optional<PriceRevision> findRevisionForProgram(Long programId, FeeType feeType, LocalDate at) {
        return priceRevisionRepository.findTopByProgramIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(programId, feeType, at);
    }
}
