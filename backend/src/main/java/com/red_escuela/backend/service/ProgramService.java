package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramRequest;
import com.red_escuela.backend.dto.UpdateProgramRequest;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
    public Program crearCarrera(CreateProgramRequest request) {
        Program program = new Program();
        program.setName(request.name());
        program.setTotalYears(request.totalYears());
        program.setPrice(request.price());
        program.setEnrollmentFeeAmount(request.enrollmentFeeAmount());
        program.setTotalCreditsRequired(request.totalCreditsRequired());
        program.setStatus(StatusType.ACTIVE);
        program = programRepository.save(program);
        registerInitialPriceRevisions(program);
        return program;
    }

    @Transactional
    public Program editarCarrera(Long id, UpdateProgramRequest request) {
        Program program = findOrThrow(id);
        Person actor = currentPersonService.resolveCurrentPerson();
        if (request.name() != null) {
            program.setName(request.name());
        }
        if (request.totalYears() != null) {
            program.setTotalYears(request.totalYears());
        }
        if (request.price() != null && request.price().compareTo(program.getPrice()) != 0) {
            program.setPrice(request.price());
            priceRevisionService.registerForProgram(program, FeeType.RECURRING_FEE, request.price(), "Ajuste de precio", actor);
        }
        if (request.enrollmentFeeAmount() != null && request.enrollmentFeeAmount().compareTo(program.getEnrollmentFeeAmount()) != 0) {
            program.setEnrollmentFeeAmount(request.enrollmentFeeAmount());
            priceRevisionService.registerForProgram(program, FeeType.ENROLLMENT_FEE, request.enrollmentFeeAmount(), "Ajuste de matrícula", actor);
        }
        if (request.totalCreditsRequired() != null) {
            program.setTotalCreditsRequired(request.totalCreditsRequired());
        }
        return programRepository.save(program);
    }

    public List<Program> listar(StatusType status) {
        return status == null ? programRepository.findAll() : programRepository.findByStatus(status);
    }

    public Program verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        Program program = findOrThrow(id);
        program.setStatus(StatusType.INACTIVE);
        programRepository.save(program);
    }

    @Transactional
    public void reactivar(Long id) {
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
