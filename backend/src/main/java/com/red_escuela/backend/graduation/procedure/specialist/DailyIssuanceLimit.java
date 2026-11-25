package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DailyIssuanceLimit {

    private final RegularStudentCertificateRepository repository;
    private final ExamCertificateRepository examCertificateRepository;
    private final Clock clock;
    private final int limitPerDay;

    public DailyIssuanceLimit(RegularStudentCertificateRepository repository, ExamCertificateRepository examCertificateRepository, Clock clock,
                              @Value("${app.certificate.documents.daily-issuance-limit:1}") int limitPerDay) {
        this.repository = repository;
        this.examCertificateRepository = examCertificateRepository;
        this.clock = clock;
        this.limitPerDay = limitPerDay;
    }

    public boolean isReachedForRegular(Long studentId) {
        long issuedToday = repository.countByStudentIdAndIssueChannelAndIssuedAtGreaterThanEqual(studentId,
                RegularCertificateChannel.STUDENT_PORTAL, LocalDate.now(clock).atStartOfDay());
        return issuedToday >= limitPerDay;
    }

    public boolean isReachedForExamCertificate(Long studentId) {
        long issuedToday = examCertificateRepository.countByStudentIdAndOriginAndIssuedAtGreaterThanEqual(studentId,
                DocumentOrigin.SELF_SERVICE, LocalDate.now(clock).atStartOfDay());
        return issuedToday >= limitPerDay;
    }
}
