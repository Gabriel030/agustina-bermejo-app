package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DailyIssuanceLimit {

    private final RegularStudentCertificateRepository repository;
    private final Clock clock;
    private final int limitPerDay;

    public DailyIssuanceLimit(RegularStudentCertificateRepository repository, Clock clock,
                              @Value("${app.certificate.documents.daily-issuance-limit:1}") int limitPerDay) {
        this.repository = repository;
        this.clock = clock;
        this.limitPerDay = limitPerDay;
    }

    public boolean isReachedForRegular(Long studentId) {
        long issuedToday = repository.countByStudentIdAndIssueChannelAndIssuedAtGreaterThanEqual(studentId,
                RegularCertificateChannel.STUDENT_PORTAL, LocalDate.now(clock).atStartOfDay());
        return issuedToday >= limitPerDay;
    }
}
