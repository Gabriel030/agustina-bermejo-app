package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.operation.LateFeeWithdrawalCorrectionStep;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

abstract class AbstractLateFeeCorrectionIT extends AbstractLateFeeScenarioIT {

    @Autowired
    protected LateFeeWithdrawalCorrectionStep correctionStep;
    @Autowired
    protected LateFeeWithdrawalCorrectionService correctionService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    protected Invoice aprilDebitNoteOrigin(int year) {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 4, 11));
        reconcile(origin, d(year, 5, 1));
        return origin;
    }

    protected Long debitNoteIdOf(Invoice origin, int index) {
        return (Long) chargesOf(origin).get(index).get("debit_note_id");
    }

    protected void endEnrollmentSilently(String status, LocalDate endDate, LocalDate registeredOn) {
        boolean withdrawn = "WITHDRAWN".equals(status);
        jdbc.update("update course_enrollment set status = ?, completion_date = ?, withdrawal_effective_date = ?, withdrawal_registered_on = ? where id = ?", status,
                Timestamp.valueOf(endDate.atTime(10, 0)), withdrawn ? Date.valueOf(endDate) : null, withdrawn ? Date.valueOf(registeredOn) : null, enrollment.getId());
    }

    protected List<Map<String, Object>> creditNotesOf(Long invoiceId) {
        return jdbc.queryForList("select * from credit_note where invoice_id = ? order by id", invoiceId);
    }

    protected BigDecimal netChargedDaysOf(Long debitNoteId) {
        return jdbc.queryForObject("select c.charged_days - coalesce((select sum(k.corrected_days) from late_fee_charge_correction k where k.late_fee_charge_id = c.id), 0) "
                + "from late_fee_charge c where c.debit_note_id = ?", BigDecimal.class, debitNoteId);
    }

    protected Person persistSecretariat() {
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person secretariat = new Person();
            String dni = "9" + System.nanoTime();
            secretariat.setName("Secretaria");
            secretariat.setLastname(dni);
            secretariat.setDni(dni);
            secretariat.setUsername(dni);
            secretariat.setEmail(dni + "@example.com");
            secretariat.setAddress(address);
            secretariat.setStatus(StatusType.ACTIVE);
            secretariat.getRoles().add(roleRepository.findByName("PRECEPTOR").orElseThrow());
            entityManager.persist(secretariat);
            return secretariat;
        });
    }

    protected long fiscalWindowNoticesOf(Person secretariat, Long debitNoteId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(secretariat.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW
                        && debitNoteId.equals(notification.getRelatedEntityId())).count();
    }
}
