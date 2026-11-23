package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.api.StudentPaymentsStandingPort;
import com.red_escuela.backend.graduation.debtexception.exception.DocumentRequiresPaymentsUpToDateException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamCertificatePaymentsGate {

    private final StudentPaymentsStandingPort standingPort;
    private final Clock clock;

    public ExamCertificatePaymentsGate(StudentPaymentsStandingPort standingPort, Clock clock) {
        this.standingPort = standingPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Set<Long> studentsWithDebt(Collection<Long> studentIds) {
        Set<Long> withDebt = new HashSet<>();
        if (studentIds.isEmpty()) {
            return withDebt;
        }
        standingPort.findPaymentsStanding(studentIds, LocalDate.now(clock)).forEach((studentId, standing) -> {
            if (standing == PaymentsStanding.NOT_OK) {
                withDebt.add(studentId);
            }
        });
        return withDebt;
    }

    @Transactional(readOnly = true)
    public void requireUpToDate(Long studentId) {
        if (!studentsWithDebt(Set.of(studentId)).isEmpty()) {
            throw new DocumentRequiresPaymentsUpToDateException();
        }
    }
}
