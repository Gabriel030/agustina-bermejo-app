package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep;
import com.red_escuela.backend.billing.collections.repository.CollectionsDailySnapshotStore;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.reporting.domain.CollectionsStockRow;
import com.red_escuela.backend.billing.reporting.domain.CollectionsStockScope;
import com.red_escuela.backend.billing.reporting.domain.OverdueAgeBucket;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionsDailySnapshotIT extends AbstractMonthlyMetricsIT {

    private static final LocalDate END_OF_MARCH = LocalDate.of(2022, 3, 31);
    private static final String NO_DISCOUNT = "0.00";

    @Autowired
    private CollectionsDailySnapshotStep snapshotStep;
    @Autowired
    private CollectionsDailySnapshotStore snapshotStore;

    @BeforeEach
    @AfterEach
    void clearSnapshots() {
        jdbc.update("delete from collections_daily_snapshot");
    }

    private Long seedOverdueInvoiceOfNewStudent(Currency currency, LocalDate dueDate, String amount, Long programId) {
        Long accountId = newAccountId(newStudentId(), currency);
        Long invoiceId = seedInvoice(accountId, currency, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, dueDate.minusDays(9), dueDate, "OVERDUE", amount,
                NO_DISCOUNT, null);
        enrollInvoiceStudentInProgram(invoiceId, programId);
        return invoiceId;
    }

    private void takeSnapshotOn(LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
        snapshotStep.takeCollectionsSnapshot(date);
    }

    private Map<String, Object> rowOf(LocalDate date, Currency currency, Long programId) {
        return jdbc.queryForMap("select * from collections_daily_snapshot where business_date = ? and currency = ? and program_id = ?", date, currency.name(), programId);
    }

    private BigDecimal totalOverdueOf(LocalDate date, Currency currency) {
        return jdbc.queryForObject("select overdue_1_30 + overdue_31_60 + overdue_61_90 + overdue_90_plus from collections_daily_snapshot "
                + "where business_date = ? and currency = ? and program_id is null and course_id is null", BigDecimal.class, date, currency.name());
    }

    private BigDecimal overdueDebtInDatabase(Currency currency, LocalDate date) {
        return jdbc.queryForObject("select coalesce(sum(outstanding_amount), 0) from invoice where document_status = 'ISSUED' and payment_status = 'OVERDUE' "
                + "and outstanding_amount > 0 and due_date < ? and currency = ?", BigDecimal.class, date, currency.name());
    }

    @Test
    @Tag("D-146")
    void overdueInvoicesAgedTenFortyFiveSeventyAndOneHundredTwentyDaysLandInTheirOwnBucketOnTheLastDayOfMarch() {
        Long program = seedProgram(Currency.ARS);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(10), "100000.00", program);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(45), "100000.00", program);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(70), "100000.00", program);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(120), "100000.00", program);

        takeSnapshotOn(END_OF_MARCH);

        Map<String, Object> row = rowOf(END_OF_MARCH, Currency.ARS, program);
        assertThat(row.get("overdue_1_30")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(row.get("overdue_31_60")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(row.get("overdue_61_90")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(row.get("overdue_90_plus")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(row.get("students_with_overdue")).isEqualTo(4);
        assertThat(row.get("enrollments_suspended")).isNull();
        assertThat(row.get("unallocated_credit_amount")).isNull();
    }

    @Test
    @Tag("D-146")
    void eachInvoiceMovesToTheNextBucketTheDayItsOwnAgeCrossesTheBorderAsTheClockAdvances() {
        Long program = seedProgram(Currency.ARS);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, LocalDate.of(2022, 3, 1), "1000.00", program);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, LocalDate.of(2022, 3, 30), "2000.00", program);

        takeSnapshotOn(LocalDate.of(2022, 3, 31));
        Map<String, Object> thirtyDaysOld = rowOf(LocalDate.of(2022, 3, 31), Currency.ARS, program);
        takeSnapshotOn(LocalDate.of(2022, 4, 1));
        Map<String, Object> thirtyOneDaysOld = rowOf(LocalDate.of(2022, 4, 1), Currency.ARS, program);

        assertThat(thirtyDaysOld.get("overdue_1_30")).isEqualTo(new BigDecimal("3000.00"));
        assertThat(thirtyDaysOld.get("overdue_31_60")).isEqualTo(new BigDecimal("0.00"));
        assertThat(thirtyOneDaysOld.get("overdue_1_30")).isEqualTo(new BigDecimal("2000.00"));
        assertThat(thirtyOneDaysOld.get("overdue_31_60")).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    @Tag("D-146")
    void anInvoiceNinetyDaysOldIsStillInTheThirdBucketAndTheNextDayItMovesToTheLast() {
        Long program = seedProgram(Currency.ARS);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, LocalDate.of(2022, 1, 1), "5000.00", program);

        takeSnapshotOn(LocalDate.of(2022, 4, 1));
        Map<String, Object> ninetyDays = rowOf(LocalDate.of(2022, 4, 1), Currency.ARS, program);
        takeSnapshotOn(LocalDate.of(2022, 4, 2));
        Map<String, Object> ninetyOneDays = rowOf(LocalDate.of(2022, 4, 2), Currency.ARS, program);

        assertThat(ninetyDays.get("overdue_61_90")).isEqualTo(new BigDecimal("5000.00"));
        assertThat(ninetyDays.get("overdue_90_plus")).isEqualTo(new BigDecimal("0.00"));
        assertThat(ninetyOneDays.get("overdue_61_90")).isEqualTo(new BigDecimal("0.00"));
        assertThat(ninetyOneDays.get("overdue_90_plus")).isEqualTo(new BigDecimal("5000.00"));
    }

    @Test
    @Tag("D-146")
    void aNotYetDueInstallmentDoesNotAddToOverdueDebtWhateverTheBalanceOfTheAccount() {
        Long program = seedProgram(Currency.ARS);
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long notDue = seedInvoice(accountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, END_OF_MARCH.minusDays(1),
                END_OF_MARCH.plusDays(9), "PENDING", "80000.00", NO_DISCOUNT, null);
        enrollInvoiceStudentInProgram(notDue, program);
        Long dueButStillPending = seedInvoice(accountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, END_OF_MARCH.minusDays(20),
                END_OF_MARCH.minusDays(10), "PENDING", "70000.00", NO_DISCOUNT, null);
        enrollInvoiceStudentInProgram(dueButStillPending, program);

        takeSnapshotOn(END_OF_MARCH);

        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ? and program_id = ?", Integer.class, END_OF_MARCH, program)).isZero();
        assertThat(totalOverdueOf(END_OF_MARCH, Currency.ARS)).isEqualByComparingTo(overdueDebtInDatabase(Currency.ARS, END_OF_MARCH));
    }

    @Test
    @Tag("D-146")
    void anOverdueInvoiceFullyPaidOrWithoutBalanceIsNotCountedAsDebt() {
        Long program = seedProgram(Currency.ARS);
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long paid = seedInvoice(accountId, Currency.ARS, InvoiceType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, END_OF_MARCH.minusDays(40),
                END_OF_MARCH.minusDays(30), "PAID", "60000.00", NO_DISCOUNT, null);
        enrollInvoiceStudentInProgram(paid, program);

        takeSnapshotOn(END_OF_MARCH);

        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ? and program_id = ?", Integer.class, END_OF_MARCH, program)).isZero();
    }

    @Test
    @Tag("D-097")
    void arsAndUsdDebtsAreStoredInSeparateRowsAndNeverSummed() {
        Long arsProgram = seedProgram(Currency.ARS);
        Long usdProgram = seedProgram(Currency.USD);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(10), "250000.00", arsProgram);
        seedOverdueInvoiceOfNewStudent(Currency.USD, END_OF_MARCH.minusDays(10), "300.00", usdProgram);

        takeSnapshotOn(END_OF_MARCH);

        assertThat(rowOf(END_OF_MARCH, Currency.ARS, arsProgram).get("overdue_1_30")).isEqualTo(new BigDecimal("250000.00"));
        assertThat(rowOf(END_OF_MARCH, Currency.USD, usdProgram).get("overdue_1_30")).isEqualTo(new BigDecimal("300.00"));
        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where program_id = ? and currency = 'USD'", Integer.class, arsProgram)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where program_id = ? and currency = 'ARS'", Integer.class, usdProgram)).isZero();
        for (Currency currency : Currency.values()) {
            assertThat(totalOverdueOf(END_OF_MARCH, currency)).isEqualByComparingTo(overdueDebtInDatabase(currency, END_OF_MARCH));
            assertThat(jdbc.queryForObject("select unallocated_credit_amount from collections_daily_snapshot where business_date = ? and currency = ? "
                    + "and program_id is null and course_id is null", BigDecimal.class, END_OF_MARCH, currency.name()))
                    .isEqualByComparingTo(jdbc.queryForObject("select coalesce(sum(unallocated_credit_amount), 0) from student_current_account where currency = ?", BigDecimal.class,
                            currency.name()));
        }
    }

    @Test
    @Tag("D-097")
    void everyDayHasATotalRowForEachCurrencyEvenWhenThereIsNoDebt() {
        takeSnapshotOn(LocalDate.of(2022, 5, 3));

        assertThat(jdbc.queryForList("select currency from collections_daily_snapshot where business_date = ? and program_id is null and course_id is null order by currency",
                String.class, LocalDate.of(2022, 5, 3))).containsExactly("ARS", "USD");
    }

    @Test
    void theNightRunningTwiceOnTheSameDayLeavesOneRowPerCurrencyAndScopeWithTheLatestFigures() {
        Long program = seedProgram(Currency.ARS);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(10), "100000.00", program);
        takeSnapshotOn(END_OF_MARCH);
        Long secondInvoice = seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(12), "50000.00", program);

        takeSnapshotOn(END_OF_MARCH);

        assertThat(secondInvoice).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ?", Integer.class, END_OF_MARCH))
                .isEqualTo(jdbc.queryForObject("select count(distinct (currency, coalesce(program_id, 0), coalesce(course_id, 0))) from collections_daily_snapshot "
                        + "where business_date = ?", Integer.class, END_OF_MARCH));
        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ? and program_id = ?", Integer.class, END_OF_MARCH, program)).isEqualTo(1);
        assertThat(rowOf(END_OF_MARCH, Currency.ARS, program).get("overdue_1_30")).isEqualTo(new BigDecimal("150000.00"));
    }

    @Test
    void aScopeWithoutDebtAtTheSecondRunDoesNotKeepItsStaleRowFromTheFirstRun() {
        Long program = seedProgram(Currency.ARS);
        Long invoiceId = seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(10), "100000.00", program);
        takeSnapshotOn(END_OF_MARCH);
        jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);

        takeSnapshotOn(END_OF_MARCH);

        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ? and program_id = ?", Integer.class, END_OF_MARCH, program)).isZero();
    }

    @Test
    void concurrentRunsOfTheSameDayNeitherDuplicateNorFail() throws Exception {
        Long program = seedProgram(Currency.ARS);
        seedOverdueInvoiceOfNewStudent(Currency.ARS, END_OF_MARCH.minusDays(10), "100000.00", program);
        List<CollectionsStockRow> rows = List.of(
                new CollectionsStockRow(END_OF_MARCH, Currency.ARS, CollectionsStockScope.TOTAL, Map.of(OverdueAgeBucket.FROM_1_TO_30, Money.of(new BigDecimal("10.00"), Currency.ARS)), 1),
                new CollectionsStockRow(END_OF_MARCH, Currency.ARS, CollectionsStockScope.ofProgram(program), Map.of(OverdueAgeBucket.FROM_1_TO_30, Money.of(new BigDecimal("10.00"), Currency.ARS)), 1));
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> runs = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                boolean throughStore = index % 2 == 0;
                runs.add(pool.submit(() -> {
                    start.await();
                    if (throughStore) {
                        snapshotStore.replaceSnapshotOf(END_OF_MARCH, rows, Map.of(), LocalDateTime.of(2022, 3, 31, 3, 1));
                    } else {
                        snapshotStep.takeCollectionsSnapshot(END_OF_MARCH);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ? and currency = 'ARS' and program_id = ?", Integer.class,
                END_OF_MARCH, program)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from collections_daily_snapshot where business_date = ? and currency = 'ARS' and program_id is null and course_id is null",
                Integer.class, END_OF_MARCH)).isEqualTo(1);
    }

    @Test
    void theUniqueIndexRejectsASecondRowOfTheSameDayCurrencyAndScope() {
        jdbc.update("insert into collections_daily_snapshot (business_date, currency, overdue_1_30, overdue_31_60, overdue_61_90, overdue_90_plus, students_with_overdue, created_at) "
                + "values (date '2022-06-01', 'ARS', 0, 0, 0, 0, 0, now())");

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "insert into collections_daily_snapshot (business_date, currency, overdue_1_30, overdue_31_60, overdue_61_90, overdue_90_plus, students_with_overdue, created_at) "
                        + "values (date '2022-06-01', 'ARS', 0, 0, 0, 0, 0, now())"));
    }
}
