package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.testsupport.ExplorationTier;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Property test that runs 60 days of the reminder jobs with random issues, payments, receipts, reversals and configuration changes")
class ReminderTimelinePropertyIT extends AbstractReminderTimelineIT {
    private static final int STUDENTS = ExplorationTier.sequences(4, 12);
    private static final LocalDate FIRST_DAY = LocalDate.of(2027, 3, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(2027, 4, 29);

    private record Participant(Student student, Course course, CourseEnrollment enrollment) {
    }

    @Test
    @Tag("D-353")
    @Tag("D-403")
    @Tag("D-346")
    @Tag("D-247")
    void randomDaysKeepOneDigestPerStudentAndNoEmailForSettledInvoices() {
        ReminderTimelineHarness harness = newHarness();
        Random random = new Random(ExplorationTier.seed("ReminderTimelinePropertyIT", 20261022L));
        Map<String, Integer> actions = new TreeMap<>();
        List<Participant> participants = new ArrayList<>();
        for (int index = 0; index < STUDENTS; index++) {
            Student student = newStudent();
            Course course = newCourse();
            participants.add(new Participant(student, course, enrollInCourse(student, course)));
            harness.track(student);
        }
        harness.issuing(day -> participants.forEach(participant -> {
            if (random.nextInt(100) < 60) {
                installmentOf(participant.enrollment(), day, day.plusDays(5 + random.nextInt(20)));
                actions.merge("issue", 1, Integer::sum);
            }
        })).during(day -> participants.forEach(participant -> randomActionOf(participant, day, random, actions)));

        harness.run(FIRST_DAY, LAST_DAY);

        assertThat(actions.keySet()).contains("issue", "pay", "uploadReceipt", "rejectReceipt", "revert", "changeConfiguration");
        long reminderRows = participants.stream().mapToLong(participant -> harness.inAppDaysOf(participant.student()).size() + harness.emailDaysOf(participant.student()).size()).sum();
        assertThat(reminderRows).isPositive();
        participants.forEach(participant -> assertThat(harness.sentEmailDaysOf(participant.student())).isSubsetOf(harness.emailDaysOf(participant.student())));
    }

    private void randomActionOf(Participant participant, LocalDate day, Random random, Map<String, Integer> actions) {
        Long studentId = participant.student().getId();
        List<Long> open = invoicesOf(studentId, "payment_status in ('PENDING', 'OVERDUE')");
        List<Long> paid = invoicesOf(studentId, "payment_status = 'PAID' and exists (select 1 from payment_allocation p where p.invoice_id = invoice.id) "
                + "and not exists (select 1 from payment_allocation r where r.reverses_allocation_id in (select id from payment_allocation where invoice_id = invoice.id))");
        List<Long> withReceipt = jdbc.queryForList("select b.id from bank_transfer_receipt b join invoice i on i.id = b.invoice_id join student_current_account a on a.id = i.student_current_account_id "
                + "where a.student_id = ? and b.status = 'PENDING_REVIEW' order by b.id", Long.class, studentId);
        switch (random.nextInt(8)) {
            case 0 -> {
                if (!open.isEmpty()) {
                    pay(invoiceById(open.get(random.nextInt(open.size()))), day);
                    actions.merge("pay", 1, Integer::sum);
                }
            }
            case 1, 2 -> {
                List<Long> withoutReceipt = open.stream().filter(id -> jdbc.queryForObject("select count(*) from bank_transfer_receipt where invoice_id = ? and status = 'PENDING_REVIEW'",
                        Long.class, id) == 0).toList();
                if (!withoutReceipt.isEmpty()) {
                    uploadReceipt(invoiceById(withoutReceipt.get(random.nextInt(withoutReceipt.size()))));
                    actions.merge("uploadReceipt", 1, Integer::sum);
                }
            }
            case 3 -> {
                if (!withReceipt.isEmpty()) {
                    rejectReceipt(withReceipt.get(random.nextInt(withReceipt.size())));
                    actions.merge("rejectReceipt", 1, Integer::sum);
                }
            }
            case 4 -> {
                if (!paid.isEmpty()) {
                    revertPayment(invoiceById(paid.get(random.nextInt(paid.size()))), day, 5);
                    actions.merge("revert", 1, Integer::sum);
                }
            }
            case 5 -> {
                publishCourseSchedule(participant.course(), day.plusDays(1), randomNotices(random));
                actions.merge("changeConfiguration", 1, Integer::sum);
            }
            default -> {
            }
        }
    }

    private List<Long> invoicesOf(Long studentId, String condition) {
        return jdbc.queryForList("select id from invoice where student_current_account_id in (select id from student_current_account where student_id = ?) and " + condition + " order by id",
                Long.class, studentId);
    }

    private static List<ReminderNotice> randomNotices(Random random) {
        List<ReminderNotice> notices = new ArrayList<>(ReminderSchedule.initial().notices().stream().filter(notice -> random.nextInt(100) < 70).toList());
        if (notices.isEmpty() || random.nextBoolean()) {
            notices.add(notice("weekly-after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1 + random.nextInt(5),
                    EnumSet.allOf(ReminderChannel.class), new ReminderRepetition(7, ReminderMilestone.SUSPENSION)));
        }
        return notices;
    }
}
