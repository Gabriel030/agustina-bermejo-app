package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Currency;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("D-089")
@Tag("D-140")
class CurrentAccountAllocationEndpointIT extends AbstractCurrentAccountIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private EntityManager entityManager;

    private String tokenWithRole(String role) {
        Person staff = newStaffPerson();
        return inTransaction(() -> {
            Person managed = entityManager.find(Person.class, staff.getId());
            managed.getRoles().add(roleRepository.findByName(role).orElseThrow());
            return jwtService.generateAccessToken(managed);
        });
    }

    @Test
    void forcingTheAllocationFromTheEndpointSettlesTheOldestInvoiceAndReturnsTheCreatedAllocation() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long march = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 3, 10));
        issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 4, 10));
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("82000.00")), ars("82000.00"), LocalDate.of(2027, 4, 1), "Pago");
            return null;
        });
        String token = tokenWithRole("ADMINISTRATIVO");

        mockMvc.perform(post("/students/" + studentId + "/current-account/allocations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.length()").value(1))
                .andExpect(jsonPath("$.allocations[0].invoiceId").value(march));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, march)).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("2000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void forcingTheAllocationTwiceAllocatesNothingTheSecondTime() throws Exception {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 3, 10));
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("80000.00")), ars("80000.00"), LocalDate.of(2027, 4, 1), "Pago");
            return null;
        });
        String token = tokenWithRole("DIRECTOR");

        mockMvc.perform(post("/students/" + studentId + "/current-account/allocations").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/students/" + studentId + "/current-account/allocations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.length()").value(0));
    }
}
