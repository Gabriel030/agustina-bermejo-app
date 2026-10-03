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
import java.util.function.Consumer;
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
        Optional.ofNullable(request.getName()).ifPresent(program::setName);
        Optional.ofNullable(request.getTotalYears()).ifPresent(program::setTotalYears);
        if (program.changeRecurringFee(request.getPrice())) {
            priceRevisionService.registerForProgram(program, FeeType.RECURRING_FEE, request.getPrice(), "Ajuste de precio", actor);
        }
        if (program.changeEnrollmentFee(request.getEnrollmentFeeAmount())) {
            priceRevisionService.registerForProgram(program, FeeType.ENROLLMENT_FEE, request.getEnrollmentFeeAmount(), "Ajuste de matrícula", actor);
        }
        Optional.ofNullable(request.getTotalCreditsRequired()).ifPresent(program::setTotalCreditsRequired);
        applyClearable(request.getTotalHoursRequired(), program::setTotalHoursRequired);
        applyClearable(request.getTeachingMode(), program::setTeachingMode);
        applyClearable(request.getLevel(), program::setLevel);
        applyClearable(blankAsEmpty(request.getGraduationProfile()), program::setGraduationProfile);
        applyClearable(blankAsEmpty(request.getAdmissionRequirements()), program::setAdmissionRequirements);
        return programRepository.save(program);
    }

    private static <T> void applyClearable(Optional<T> requested, Consumer<T> setter) {
        if (requested != null) {
            setter.accept(requested.orElse(null));
        }
    }

    private static Optional<String> blankAsEmpty(Optional<String> requested) {
        return requested == null ? null : requested.map(String::trim).filter(text -> !text.isEmpty());
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
