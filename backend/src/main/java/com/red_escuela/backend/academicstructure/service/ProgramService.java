package com.red_escuela.backend.academicstructure.service;
import com.red_escuela.backend.identity.service.CurrentPersonService;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceCatalog;
import com.red_escuela.backend.shared.pricing.OfferingReference;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final OfferingPriceCatalog offeringPriceCatalog;
    private final CurrentPersonService currentPersonService;

    public ProgramService(ProgramRepository programRepository, OfferingPriceCatalog offeringPriceCatalog, CurrentPersonService currentPersonService) {
        this.programRepository = programRepository;
        this.offeringPriceCatalog = offeringPriceCatalog;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Program create(CreateProgramRequest request) {
        Currency currency = resolveCurrency(request.price(), request.enrollmentFeeAmount(), request.currency());
        Program program = Program.builder()
                .name(request.name())
                .totalYears(request.totalYears())
                .price(request.price() == null ? null : request.price().amount())
                .enrollmentFeeAmount(request.enrollmentFeeAmount() == null ? null : request.enrollmentFeeAmount().amount())
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
        registerInitialOfferingPrices(program);
        return program;
    }

    @Transactional
    public Program update(Long id, UpdateProgramRequest request) {
        Program program = findOrThrow(id);
        Person actor = currentPersonService.resolveCurrentPerson();
        Optional.ofNullable(request.getName()).ifPresent(program::setName);
        Optional.ofNullable(request.getTotalYears()).ifPresent(program::setTotalYears);
        if (program.changeRecurringFee(request.getPrice())) {
            offeringPriceCatalog.adjustOfferingPrice(OfferingReference.ofProgram(program.getId()), OfferingFee.MONTHLY_INSTALLMENT, request.getPrice(), actor.getId());
        }
        if (program.changeEnrollmentFee(request.getEnrollmentFeeAmount())) {
            offeringPriceCatalog.adjustOfferingPrice(OfferingReference.ofProgram(program.getId()), OfferingFee.ENROLLMENT_FEE, request.getEnrollmentFeeAmount(), actor.getId());
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

    private void registerInitialOfferingPrices(Program program) {
        Person actor = currentPersonService.resolveCurrentPerson();
        Money tuition = Optional.ofNullable(program.priceAsMoney()).orElseGet(() -> Money.zero(program.getCurrency()));
        offeringPriceCatalog.registerInitialOfferingPrice(OfferingReference.ofProgram(program.getId()), OfferingFee.MONTHLY_INSTALLMENT, tuition, actor.getId());
        Money enrollmentFee = Optional.ofNullable(program.enrollmentFeeAsMoney()).orElseGet(() -> Money.zero(program.getCurrency()));
        offeringPriceCatalog.registerInitialOfferingPrice(OfferingReference.ofProgram(program.getId()), OfferingFee.ENROLLMENT_FEE, enrollmentFee, actor.getId());
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
