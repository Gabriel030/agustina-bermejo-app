package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.MonetaryAdjustmentValueRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Regresión (auditoría 2026-09-04): una PaymentPlanOption UPFRONT mal configurada (ventana
// habilitada sin fechas, o start posterior a end) explotaba con NullPointerException recién
// cuando un alumno intentaba seleccionar el plan, en vez de rechazar la creación con 400.
@SpringBootTest
@ActiveProfiles("test")
class PaymentPlanOptionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForCourseUseCase createPaymentPlanOptionForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForProgramUseCase createPaymentPlanOptionForProgramUseCase;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ProgramRepository programRepository;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Person persistDirector(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private void autenticarComo(Person person) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Program persistPrograma() {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    @Test
    void upfrontHabilitadoSinFechasSeRechaza() {
        autenticarComo(persistDirector("director.plan1." + System.nanoTime()));
        Program program = persistPrograma();
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, null, null);

        assertThatThrownBy(() -> createPaymentPlanOptionForProgramUseCase.create(program.getId(), request))
                .isInstanceOf(InvalidWindowConfigurationException.class);
    }

    @Test
    void upfrontConStartPosteriorAEndSeRechaza() {
        autenticarComo(persistDirector("director.plan2." + System.nanoTime()));
        Program program = persistPrograma();
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, LocalDate.now().plusDays(10), LocalDate.now());

        assertThatThrownBy(() -> createPaymentPlanOptionForProgramUseCase.create(program.getId(), request))
                .isInstanceOf(InvalidWindowConfigurationException.class);
    }

    @Test
    void upfrontConVentanaValidaSeCreaCorrectamente() {
        autenticarComo(persistDirector("director.plan3." + System.nanoTime()));
        Program program = persistPrograma();
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().plusDays(30);
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, start, end);

        var option = createPaymentPlanOptionForProgramUseCase.create(program.getId(), request);

        // Corrección 2026-09-04 (auditoría de calidad de tests): el único assert previo miraba
        // isUpfrontWindowEnabled() (un booleano ajeno a las fechas) — un bug que dejara
        // upfrontWindowStart/End sin persistir (exactamente el tipo de bug que motivó la
        // validación de PaymentPlanOptionService.validateUpfrontWindow) hubiera pasado inadvertido.
        assertThat(option.isUpfrontWindowEnabled()).isTrue();
        assertThat(option.getUpfrontWindowStart()).isEqualTo(start);
        assertThat(option.getUpfrontWindowEnd()).isEqualTo(end);
    }

    @Test
    void monthlySinVentanaNoSeValida() {
        autenticarComo(persistDirector("director.plan4." + System.nanoTime()));
        Program program = persistPrograma();
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null);

        var option = createPaymentPlanOptionForProgramUseCase.create(program.getId(), request);

        assertThat(option.getCadence()).isEqualTo(PaymentPlanCadence.MONTHLY);
    }

    // Gap de test detectado en la auditoría 2026-09-04: MonetaryAdjustmentValueRequest.toValue()
    // ("exactamente uno de los dos, nunca ambos ni ninguno") no tenía ningún test dedicado.
    @Test
    void ambosValoresALaVezSeRechazan() {
        assertThatThrownBy(() -> new MonetaryAdjustmentValueRequest(BigDecimal.TEN, BigDecimal.valueOf(0.1)).toValue())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ningunValorSeRechaza() {
        assertThatThrownBy(() -> new MonetaryAdjustmentValueRequest(null, null).toValue())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
