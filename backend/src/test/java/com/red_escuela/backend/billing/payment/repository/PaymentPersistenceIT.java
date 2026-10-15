package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CardPaymentType;
import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.dto.PaymentResponse;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private StudentCurrentAccountRepository accountRepository;
    @Autowired
    private com.red_escuela.backend.billing.repository.PaymentRepository legacyPaymentRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Person person;
    private Long accountId;

    @BeforeEach
    void setUp() {
        person = transactionTemplate.execute(status -> {
            Person created = new Person();
            String username = "payment." + System.nanoTime();
            created.setUsername(username);
            created.setDni(Long.toString(Math.abs((long) username.hashCode())));
            created.setName("Ana");
            created.setLastname("Pérez");
            created.setEmail(username + "@example.com");
            created.setAddress(addressRepository.save(new Address()));
            return personRepository.save(created);
        });
        Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
        if (institutionId == null) {
            jdbc.update("insert into institution (name) values ('Instituto')");
            institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
        }
        jdbc.update("insert into student (person_id, institution_id) values (?, ?)", person.getId(), institutionId);
        Long studentId = jdbc.queryForObject("select max(id) from student", Long.class);
        accountId = transactionTemplate.execute(status -> accountRepository.save(StudentCurrentAccount.openStudentCurrentAccount(studentId, Currency.ARS)).getId());
    }

    private StudentCurrentAccount account() {
        return accountRepository.findById(accountId).orElseThrow();
    }

    private static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    @Tag("D-079")
    @Tag("D-082")
    void aCashPaymentIsStoredAndReadBackWithItsDetailsHistoryAndNoCardData() {
        StatusChangeActor actor = StatusChangeActor.ofPerson(person.getId());
        Long id = transactionTemplate.execute(status -> paymentRepository.save(Payment.registerApprovedCashPayment(
                account(), ars("80000.00"), LocalDate.of(2027, 3, 15), new CashPaymentDetails("R-77"), "key-it-cash", actor)).getId());

        Payment reloaded = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findById(id).orElseThrow();
            payment.statusChanges().size();
            return payment;
        });

        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(reloaded.getMethodDetails()).isEqualTo(new CashPaymentDetails("R-77"));
        assertThat(reloaded.getMethodType()).isEqualTo(PaymentMethodType.CASH);
        assertThat(reloaded.getAmount()).isEqualTo(ars("80000.00"));
        assertThat(reloaded.getApprovedAmount()).isEqualTo(ars("80000.00"));
        assertThat(reloaded.getPaidOn()).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(reloaded.getVersion()).isNotNull();
        assertThat(reloaded.statusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getEvent()).isEqualTo("CASH_PAYMENT_REGISTERED");
            assertThat(change.getActorPersonId()).isEqualTo(person.getId());
        });
        assertThat(jdbc.queryForObject("select method_details::text from payment where id = ?", String.class, id)).contains("\"type\": \"CASH\"").doesNotContain("installments", "cardType");
    }

    @Test
    @Tag("D-082")
    void everyOtherVariantOfTheMethodDetailsSurvivesTheJsonbColumnAndAReviewAddsItsHistoryRow() {
        StatusChangeActor actor = StatusChangeActor.ofPerson(person.getId());
        BankTransferPaymentDetails transfer = new BankTransferPaymentDetails("Banco Nación", "0110000000000000000001", "OP-9", "Ana Pérez", LocalDate.of(2027, 3, 10));
        Long transferId = transactionTemplate.execute(status -> paymentRepository.save(Payment.createPendingBankTransferPayment(account(), ars("100.00"), transfer, "key-it-transfer", actor)).getId());
        Long cardId = transactionTemplate.execute(status -> paymentRepository.save(Payment.createPendingGatewayPayment(account(), ars("100.00"), PaymentGatewayCode.MERCADO_PAGO,
                new CardPaymentDetails(CardPaymentType.CREDIT, 3, "4242"), "mp-it-1", new GatewayRawStatus("pending", "waiting"), "key-it-card", actor)).getId());

        transactionTemplate.executeWithoutResult(status -> paymentRepository.findById(transferId).orElseThrow()
                .approvePayment(ars("60.00"), LocalDate.of(2027, 3, 12), actor, "verificado por menos"));

        transactionTemplate.executeWithoutResult(status -> {
            Payment approved = paymentRepository.findById(transferId).orElseThrow();
            assertThat(approved.getMethodDetails()).isEqualTo(transfer);
            assertThat(approved.getApprovedAmount()).isEqualTo(ars("60.00"));
            assertThat(approved.getAmount()).isEqualTo(ars("100.00"));
            assertThat(approved.statusChanges()).extracting(change -> change.getToStatus()).containsExactly("PENDING", "APPROVED");
            Payment card = paymentRepository.findById(cardId).orElseThrow();
            assertThat(card.getMethodDetails()).isEqualTo(new CardPaymentDetails(CardPaymentType.CREDIT, 3, "4242"));
            assertThat(card.getGatewayRawStatus()).isEqualTo("pending");
            assertThat(card.getGatewayPaymentId()).isEqualTo("mp-it-1");
        });
        assertThat(jdbc.queryForObject("select method_details->>'transferDate' from payment where id = ?", String.class, transferId)).isEqualTo("2027-03-10");
        assertThat(jdbc.queryForObject("select count(*) from payment_status_change where payment_id = ?", Long.class, transferId)).isEqualTo(2);
    }

    @Test
    @Tag("D-082")
    @Tag("D-144")
    void theLegacyPaymentEntityStillReadsAndMapsItsRowsFromTheRenamedTable() {
        Long legacyId = transactionTemplate.execute(status -> {
            com.red_escuela.backend.billing.entity.Payment legacy = new com.red_escuela.backend.billing.entity.Payment();
            legacy.setAmount(Money.of(new BigDecimal("1500.50"), Currency.ARS));
            legacy.setMethod(com.red_escuela.backend.billing.entity.PaymentMethod.CASH);
            legacy.setRegisteredBy(personRepository.findById(person.getId()).orElseThrow());
            legacy.setPaidAt(LocalDateTime.of(2027, 3, 1, 10, 0));
            legacy.setStatus(com.red_escuela.backend.billing.entity.PaymentStatus.CONFIRMED);
            return legacyPaymentRepository.save(legacy).getId();
        });

        PaymentResponse response = transactionTemplate.execute(status -> PaymentResponse.from(legacyPaymentRepository.findById(legacyId).orElseThrow()));

        assertThat(response.id()).isEqualTo(legacyId);
        assertThat(response.amount()).isEqualTo(Money.of(new BigDecimal("1500.50"), Currency.ARS));
        assertThat(response.method()).isEqualTo("CASH");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.registeredByName()).isEqualTo("Ana Pérez");
        assertThat(response.paidAt()).isEqualTo(LocalDateTime.of(2027, 3, 1, 10, 0));
        assertThat(response.allocations()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from legacy_payment where id = ?", Long.class, legacyId)).isEqualTo(1);
    }
}
