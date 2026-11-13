package com.red_escuela.backend.academicstructure.service;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.exception.OfferingListPriceNotPositiveException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceCatalog;
import com.red_escuela.backend.shared.pricing.OfferingReference;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final OfferingPriceCatalog offeringPriceCatalog;
    private final OfferingListPriceReader listPriceReader;
    private final CurrentPersonService currentPersonService;

    public ProgramService(ProgramRepository programRepository, OfferingPriceCatalog offeringPriceCatalog, OfferingListPriceReader listPriceReader,
                          CurrentPersonService currentPersonService) {
        this.programRepository = programRepository;
        this.offeringPriceCatalog = offeringPriceCatalog;
        this.listPriceReader = listPriceReader;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Program create(CreateProgramRequest request) {
        requirePositive(request.price(), "la cuota");
        requirePositive(request.enrollmentFeeAmount(), "la matrícula");
        Currency currency = resolveCurrency(request.price(), request.enrollmentFeeAmount(), request.currency());
        Program program = Program.builder()
                .name(request.name())
                .totalYears(request.totalYears())
                .totalCreditsRequired(request.totalCreditsRequired())
                .totalHoursRequired(request.totalHoursRequired())
                .teachingMode(request.teachingMode())
                .level(request.level())
                .graduationProfile(request.graduationProfile())
                .admissionRequirements(request.admissionRequirements())
                .status(StatusType.ACTIVE)
                .currency(currency)
                .build();
        program = programRepository.save(program);
        registerInitialOfferingPrices(program, request.price(), request.enrollmentFeeAmount());
        return withListPrices(program);
    }

    @Transactional
    public Program update(Long id, UpdateProgramRequest request) {
        Program program = findOrThrow(id);
        requirePositiveWhenPresent(request.getPrice(), "la cuota");
        requirePositiveWhenPresent(request.getEnrollmentFeeAmount(), "la matrícula");
        Person actor = currentPersonService.resolveCurrentPerson();
        Optional.ofNullable(request.getName()).ifPresent(program::setName);
        Optional.ofNullable(request.getTotalYears()).ifPresent(program::setTotalYears);
        adjustIfChanged(program, OfferingFee.MONTHLY_INSTALLMENT, request.getPrice(), actor, listPriceReader.findProgramTuition(program.getId()));
        adjustIfChanged(program, OfferingFee.ENROLLMENT_FEE, request.getEnrollmentFeeAmount(), actor, listPriceReader.findProgramEnrollmentFee(program.getId()));
        Optional.ofNullable(request.getTotalCreditsRequired()).ifPresent(program::setTotalCreditsRequired);
        applyClearable(request.getTotalHoursRequired(), program::setTotalHoursRequired);
        applyClearable(request.getTeachingMode(), program::setTeachingMode);
        applyClearable(request.getLevel(), program::setLevel);
        applyClearable(blankAsEmpty(request.getGraduationProfile()), program::setGraduationProfile);
        applyClearable(blankAsEmpty(request.getAdmissionRequirements()), program::setAdmissionRequirements);
        return withListPrices(programRepository.save(program));
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
        List<Program> programs = programRepository.findAll(Sort.by("name", "id")).stream()
                .filter(program -> status == null || program.getStatus() == status)
                .filter(program -> visibleInCatalog == null || program.isVisibleInCatalog() == visibleInCatalog)
                .toList();
        List<Long> ids = programs.stream().map(Program::getId).toList();
        Map<Long, Money> tuitions = listPriceReader.findProgramTuitions(ids);
        Map<Long, Money> enrollmentFees = listPriceReader.findProgramEnrollmentFees(ids);
        programs.forEach(program -> program.showListPrices(tuitions.get(program.getId()), enrollmentFees.get(program.getId())));
        return programs;
    }

    public Program get(Long id) {
        return withListPrices(findOrThrow(id));
    }

    private Program withListPrices(Program program) {
        program.showListPrices(listPriceReader.findProgramTuition(program.getId()).orElse(null),
                listPriceReader.findProgramEnrollmentFee(program.getId()).orElse(null));
        return program;
    }

    private static void requirePositive(Money price, String concept) {
        if (price == null || price.amount().signum() <= 0) {
            throw new OfferingListPriceNotPositiveException(concept);
        }
    }

    private static void requirePositiveWhenPresent(Money price, String concept) {
        if (price != null) {
            requirePositive(price, concept);
        }
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

    private void adjustIfChanged(Program program, OfferingFee fee, Money requested, Person actor, Optional<Money> current) {
        if (requested == null) {
            return;
        }
        program.requireProgramCurrency(requested);
        if (current.filter(price -> price.amount().compareTo(requested.amount()) == 0).isPresent()) {
            return;
        }
        offeringPriceCatalog.adjustOfferingPrice(OfferingReference.ofProgram(program.getId()), fee, requested, actor.getId());
    }

    private void registerInitialOfferingPrices(Program program, Money tuitionRequested, Money enrollmentFeeRequested) {
        Person actor = currentPersonService.resolveCurrentPerson();
        offeringPriceCatalog.registerInitialOfferingPrice(OfferingReference.ofProgram(program.getId()), OfferingFee.MONTHLY_INSTALLMENT, tuitionRequested, actor.getId());
        offeringPriceCatalog.registerInitialOfferingPrice(OfferingReference.ofProgram(program.getId()), OfferingFee.ENROLLMENT_FEE, enrollmentFeeRequested, actor.getId());
    }

    private static Currency resolveCurrency(Money price, Money enrollmentFee, Currency requested) {
        Currency currency = Optional.ofNullable(price).map(Money::currency)
                .or(() -> Optional.ofNullable(enrollmentFee).map(Money::currency))
                .or(() -> Optional.ofNullable(requested))
                .orElse(Currency.ARS);
        if (requested != null && requested != currency) {
            throw new CurrencyMismatchException(requested, currency);
        }
        if (enrollmentFee != null && enrollmentFee.currency() != currency) {
            throw new CurrencyMismatchException(currency, enrollmentFee.currency());
        }
        return currency;
    }

    private Program findOrThrow(Long id) {
        return programRepository.findById(id).orElseThrow(() -> new ProgramNotFoundException(id));
    }
}
