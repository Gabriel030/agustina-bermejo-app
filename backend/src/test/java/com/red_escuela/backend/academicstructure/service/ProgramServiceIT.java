package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.exception.OfferingListPriceNotPositiveException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramServiceIT extends AbstractIntegrationTest {

    private static final long MISSING_ID = 987_654_321L;

    @Autowired
    private ProgramService programService;
    @Autowired
    private OfferingListPriceReader listPriceReader;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void authenticateAStaffMember() {
        Person staff = new CycleTestFixtures(entityManager).person();
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), Currency.USD);
    }

    private CreateProgramRequest createRequest(String name, Money price, Money enrollmentFee, Currency currency) {
        return new CreateProgramRequest(name, 3, price, enrollmentFee, 100, null, null, null, null, null, currency);
    }

    private Program createProgram(String name) {
        return programService.create(createRequest(name, ars("1000"), ars("500"), null));
    }

    @Test
    void aProgramWithPricesAndNoCurrencyDefaultsToPesosAndStartsActive() {
        Program program = programService.create(createRequest("Con precios", ars("1000"), ars("500"), null));

        assertThat(program.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(program.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    @Tag("D-536")
    void aProgramWithoutTuitionOrWithoutEnrollmentFeeIsRejected() {
        assertThatThrownBy(() -> programService.create(createRequest("Sin cuota", null, ars("500"), null)))
                .isInstanceOf(OfferingListPriceNotPositiveException.class);
        assertThatThrownBy(() -> programService.create(createRequest("Sin matricula", ars("1000"), null, null)))
                .isInstanceOf(OfferingListPriceNotPositiveException.class);
        assertThatThrownBy(() -> programService.create(createRequest("Sin nada", null, null, Currency.USD)))
                .isInstanceOf(OfferingListPriceNotPositiveException.class);
    }

    @Test
    @Tag("D-536")
    void aProgramWithAZeroTuitionOrEnrollmentFeeIsRejected() {
        assertThatThrownBy(() -> programService.create(createRequest("Cuota cero", ars("0"), ars("500"), null)))
                .isInstanceOf(OfferingListPriceNotPositiveException.class);
        assertThatThrownBy(() -> programService.create(createRequest("Matricula cero", ars("1000"), ars("0"), null)))
                .isInstanceOf(OfferingListPriceNotPositiveException.class);
    }

    @Test
    @Tag("D-536")
    void updatingAProgramPriceToZeroIsRejectedAndLeavesThePriceUntouched() {
        Program program = createProgram("Update a cero");
        UpdateProgramRequest toZero = new UpdateProgramRequest();
        toZero.setPrice(ars("0"));
        UpdateProgramRequest feeToZero = new UpdateProgramRequest();
        feeToZero.setEnrollmentFeeAmount(ars("0"));

        assertThatThrownBy(() -> programService.update(program.getId(), toZero)).isInstanceOf(OfferingListPriceNotPositiveException.class);
        assertThatThrownBy(() -> programService.update(program.getId(), feeToZero)).isInstanceOf(OfferingListPriceNotPositiveException.class);
        assertThat(listPriceReader.findProgramTuition(program.getId())).contains(ars("1000"));
        assertThat(listPriceReader.findProgramEnrollmentFee(program.getId())).contains(ars("500"));
    }

    @Test
    void theProgramCurrencyFollowsTheCurrencyOfItsPrice() {
        Program program = programService.create(createRequest("En dolares", usd("20"), usd("10"), null));

        assertThat(program.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void aRequestedCurrencyDifferentFromThePriceCurrencyIsRejected() {
        assertThatThrownBy(() -> programService.create(createRequest("Moneda cruzada", ars("100"), ars("50"), Currency.USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void anEnrollmentFeeInAnotherCurrencyThanThePriceIsRejected() {
        assertThatThrownBy(() -> programService.create(createRequest("Matricula cruzada", ars("100"), usd("10"), null)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void updatingOnlyTheNameKeepsEveryOtherField() {
        Program program = createProgram("Nombre viejo");
        UpdateProgramRequest request = new UpdateProgramRequest();
        request.setName("Nombre nuevo");

        Program updated = programService.update(program.getId(), request);

        assertThat(updated.getName()).isEqualTo("Nombre nuevo");
        assertThat(updated.getTotalYears()).isEqualTo(3);
        assertThat(listPriceReader.findProgramTuition(updated.getId()).orElse(null)).isEqualTo(ars("1000"));
        assertThat(listPriceReader.findProgramEnrollmentFee(updated.getId()).orElse(null)).isEqualTo(ars("500"));
    }

    @Test
    void updatingThePricesStoresTheNewAmounts() {
        Program program = createProgram("Precios");
        UpdateProgramRequest request = new UpdateProgramRequest();
        request.setPrice(ars("1500"));
        request.setEnrollmentFeeAmount(ars("700"));

        Program updated = programService.update(program.getId(), request);

        assertThat(listPriceReader.findProgramTuition(updated.getId()).orElse(null)).isEqualTo(ars("1500"));
        assertThat(listPriceReader.findProgramEnrollmentFee(updated.getId()).orElse(null)).isEqualTo(ars("700"));
    }

    @Test
    void updatingThePriceWithAnotherCurrencyThanTheProgramIsRejected() {
        Program program = createProgram("Moneda fija");
        UpdateProgramRequest request = new UpdateProgramRequest();
        request.setPrice(usd("10"));

        assertThatThrownBy(() -> programService.update(program.getId(), request))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void updatingTheOptionalFieldsWithAValueSetsThemAndWithAnEmptyOptionalClearsThem() {
        Program program = createProgram("Opcionales");
        UpdateProgramRequest set = new UpdateProgramRequest();
        set.setTotalHoursRequired(Optional.of(200));
        set.setTeachingMode(Optional.of(TeachingMode.ONLINE));
        set.setLevel(Optional.of(ProgramLevel.GRADUATE));
        set.setGraduationProfile(Optional.of("  Perfil del egresado  "));
        set.setAdmissionRequirements(Optional.of("Secundario completo"));
        programService.update(program.getId(), set);

        assertThat(program.getTotalHoursRequired()).isEqualTo(200);
        assertThat(program.getTeachingMode()).isEqualTo(TeachingMode.ONLINE);
        assertThat(program.getLevel()).isEqualTo(ProgramLevel.GRADUATE);
        assertThat(program.getGraduationProfile()).isEqualTo("Perfil del egresado");
        assertThat(program.getAdmissionRequirements()).isEqualTo("Secundario completo");

        UpdateProgramRequest clear = new UpdateProgramRequest();
        clear.setTotalHoursRequired(Optional.empty());
        clear.setTeachingMode(Optional.empty());
        clear.setLevel(Optional.empty());
        clear.setGraduationProfile(Optional.of("   "));
        clear.setAdmissionRequirements(Optional.empty());
        programService.update(program.getId(), clear);

        assertThat(program.getTotalHoursRequired()).isNull();
        assertThat(program.getTeachingMode()).isNull();
        assertThat(program.getLevel()).isNull();
        assertThat(program.getGraduationProfile()).isNull();
        assertThat(program.getAdmissionRequirements()).isNull();
    }

    @Test
    void updatingAnUnknownProgramReturnsNotFound() {
        assertThatThrownBy(() -> programService.update(MISSING_ID, new UpdateProgramRequest()))
                .isInstanceOf(ProgramNotFoundException.class);
        assertThatThrownBy(() -> programService.get(MISSING_ID)).isInstanceOf(ProgramNotFoundException.class);
        assertThatThrownBy(() -> programService.deactivate(MISSING_ID)).isInstanceOf(ProgramNotFoundException.class);
        assertThatThrownBy(() -> programService.reactivate(MISSING_ID)).isInstanceOf(ProgramNotFoundException.class);
    }

    @Test
    void deactivatingAndReactivatingAProgramTogglesItsStatusAndTheStatusFilter() {
        Program program = createProgram("Alternar");

        programService.deactivate(program.getId());
        assertThat(programService.list(StatusType.INACTIVE, null)).extracting(Program::getId).contains(program.getId());
        assertThat(programService.list(StatusType.ACTIVE, null)).extracting(Program::getId).doesNotContain(program.getId());

        programService.reactivate(program.getId());
        assertThat(programService.list(StatusType.ACTIVE, null)).extracting(Program::getId).contains(program.getId());
    }

    @Test
    void listingFiltersByCatalogVisibilityAndSortsByName() {
        Program visible = createProgram("AAA visible");
        Program hidden = createProgram("AAB oculto");
        visible.setVisibleInCatalog(true);
        entityManager.flush();

        List<Program> onlyHidden = programService.list(null, false);
        List<Program> onlyVisible = programService.list(null, true);
        List<Program> all = programService.list(null, null);

        assertThat(onlyHidden).extracting(Program::getId).contains(hidden.getId()).doesNotContain(visible.getId());
        assertThat(onlyVisible).extracting(Program::getId).contains(visible.getId()).doesNotContain(hidden.getId());
        assertThat(all).extracting(Program::getId).containsSubsequence(visible.getId(), hidden.getId());
    }
}
