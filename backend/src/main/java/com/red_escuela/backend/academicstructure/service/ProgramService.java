package com.red_escuela.backend.academicstructure.service;
import com.red_escuela.backend.identity.service.CurrentPersonService;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.red_escuela.backend.billing.service.PriceRevisionService;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final PriceRevisionService priceRevisionService;
    private final CurrentPersonService currentPersonService;

    public ProgramService(ProgramRepository programRepository, PriceRevisionService priceRevisionService, CurrentPersonService currentPersonService) {
        this.programRepository = programRepository;
        this.priceRevisionService = priceRevisionService;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Program create(CreateProgramRequest request) {
        Program program = Program.builder()
                .name(request.name())
                .totalYears(request.totalYears())
                .price(request.price())
                .enrollmentFeeAmount(request.enrollmentFeeAmount())
                .totalCreditsRequired(request.totalCreditsRequired())
                .totalHoursRequired(request.totalHoursRequired())
                .teachingMode(request.teachingMode())
                .level(request.level())
                .graduationProfile(request.graduationProfile())
                .admissionRequirements(request.admissionRequirements())
                .status(StatusType.ACTIVE)
                .build();
        program = programRepository.save(program);
        registerInitialPriceRevisions(program);
        return program;
    }

    @Transactional
    public Program update(Long id, UpdateProgramRequest request) {
        Program program = findOrThrow(id);
        Person actor = currentPersonService.resolveCurrentPerson();
        Optional.ofNullable(request.name()).ifPresent(program::setName);
        Optional.ofNullable(request.totalYears()).ifPresent(program::setTotalYears);
        if (request.price() != null && request.price().compareTo(program.getPrice()) != 0) {
            program.setPrice(request.price());
            priceRevisionService.registerForProgram(program, FeeType.RECURRING_FEE, request.price(), "Ajuste de precio", actor);
        }
        if (request.enrollmentFeeAmount() != null && request.enrollmentFeeAmount().compareTo(program.getEnrollmentFeeAmount()) != 0) {
            program.setEnrollmentFeeAmount(request.enrollmentFeeAmount());
            priceRevisionService.registerForProgram(program, FeeType.ENROLLMENT_FEE, request.enrollmentFeeAmount(), "Ajuste de matrícula", actor);
        }
        Optional.ofNullable(request.totalCreditsRequired()).ifPresent(program::setTotalCreditsRequired);
        Optional.ofNullable(request.totalHoursRequired()).ifPresent(program::setTotalHoursRequired);
        Optional.ofNullable(request.teachingMode()).ifPresent(program::setTeachingMode);
        Optional.ofNullable(request.level()).ifPresent(program::setLevel);
        Optional.ofNullable(request.graduationProfile()).ifPresent(program::setGraduationProfile);
        Optional.ofNullable(request.admissionRequirements()).ifPresent(program::setAdmissionRequirements);
        return programRepository.save(program);
    }

    public List<Program> list(StatusType status, Boolean visibleInCatalog) {
        return programRepository.findAll(Sort.by("name", "id")).stream()
                .filter(program -> status == null || program.getStatus() == status)
                .filter(program -> visibleInCatalog == null || program.isVisibleInCatalog() == visibleInCatalog)
                .toList();
    }

    public Program get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Program program = findOrThrow(id);
        program.setStatus(StatusType.INACTIVE);
        programRepository.save(program);
    }

    @Transactional
    public void reactivate(Long id) {
        Program program = findOrThrow(id);
        program.setStatus(StatusType.ACTIVE);
        programRepository.save(program);
    }

    private void registerInitialPriceRevisions(Program program) {
        Person actor = currentPersonService.resolveCurrentPerson();
        BigDecimal tuition = program.getPrice() == null ? BigDecimal.ZERO : program.getPrice();
        priceRevisionService.registerForProgram(program, FeeType.RECURRING_FEE, tuition, "Precio inicial", actor);
        BigDecimal enrollmentFee = program.getEnrollmentFeeAmount() == null ? BigDecimal.ZERO : program.getEnrollmentFeeAmount();
        priceRevisionService.registerForProgram(program, FeeType.ENROLLMENT_FEE, enrollmentFee, "Matrícula inicial", actor);
    }

    private Program findOrThrow(Long id) {
        return programRepository.findById(id).orElseThrow(() -> new ProgramNotFoundException(id));
    }
}
