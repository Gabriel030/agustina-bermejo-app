package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
public class GraduationSnapshotFactory {

    private final Clock clock;
    private final String signerName;
    private final String signerTitle;

    public GraduationSnapshotFactory(Clock clock,
                                     @Value("${app.certificate.default-signer-name}") String signerName,
                                     @Value("${app.certificate.default-signer-title}") String signerTitle) {
        this.clock = clock;
        this.signerName = signerName;
        this.signerTitle = signerTitle;
    }

    public CertificateSnapshot create(Student student, String titleKind, String titleName, String programLevel,
                                      Integer hours, LocalDate graduationDate) {
        Person person = student.getPerson();
        return new CertificateSnapshot(
                (person.getLastname() + ", " + person.getName()).toUpperCase(Locale.ROOT),
                person.getDocumentType().label(),
                person.getDni(),
                titleKind,
                titleName,
                programLevel,
                hours,
                graduationDate,
                LocalDate.now(clock),
                student.getInstitution().getName(),
                List.of(new SignatorySnapshot(signerName, signerTitle)),
                BuiltInCertificateTemplate.BUILT_IN_V1);
    }
}
