package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local & demo")
@Order(100)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final InstitutionRepository institutionRepository;
    private final DemoPeopleSeeder peopleSeeder;
    private final DemoAcademicOfferSeeder academicOfferSeeder;
    private final DemoEnrollmentSeeder enrollmentSeeder;
    private final DemoEvaluationSeeder evaluationSeeder;
    private final DemoAttendanceSeeder attendanceSeeder;
    private final DemoBillingSeeder billingSeeder;
    private final DemoAnnouncementSeeder announcementSeeder;
    private final DemoApprovalSeeder approvalSeeder;
    private final DemoEdgeCaseSeeder edgeCaseSeeder;

    public DemoSeedRunner(
            InstitutionRepository institutionRepository,
            DemoPeopleSeeder peopleSeeder,
            DemoAcademicOfferSeeder academicOfferSeeder,
            DemoEnrollmentSeeder enrollmentSeeder,
            DemoEvaluationSeeder evaluationSeeder,
            DemoAttendanceSeeder attendanceSeeder,
            DemoBillingSeeder billingSeeder,
            DemoAnnouncementSeeder announcementSeeder,
            DemoApprovalSeeder approvalSeeder,
            DemoEdgeCaseSeeder edgeCaseSeeder) {
        this.institutionRepository = institutionRepository;
        this.peopleSeeder = peopleSeeder;
        this.academicOfferSeeder = academicOfferSeeder;
        this.enrollmentSeeder = enrollmentSeeder;
        this.evaluationSeeder = evaluationSeeder;
        this.attendanceSeeder = attendanceSeeder;
        this.billingSeeder = billingSeeder;
        this.announcementSeeder = announcementSeeder;
        this.approvalSeeder = approvalSeeder;
        this.edgeCaseSeeder = edgeCaseSeeder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (institutionRepository.count() > 0) {
            log.info("Seed demo: ya hay datos cargados, no se hace nada");
            return;
        }
        long startedAt = System.currentTimeMillis();
        DemoDataset dataset = new DemoDataset();
        peopleSeeder.seed(dataset);
        academicOfferSeeder.seed(dataset);
        enrollmentSeeder.seed(dataset);
        evaluationSeeder.seed(dataset);
        attendanceSeeder.seed(dataset);
        billingSeeder.seed(dataset);
        announcementSeeder.seed(dataset);
        approvalSeeder.seed(dataset);
        edgeCaseSeeder.seed(dataset);
        log.info("Seed demo listo en {} ms: {} carreras, {} cursos, {} comisiones, {} alumnos, {} docentes, "
                        + "{} inscripciones a carrera, {} inscripciones a materia",
                System.currentTimeMillis() - startedAt,
                dataset.getPrograms().size(),
                dataset.getCourses().size(),
                dataset.getSections().size(),
                dataset.getStudents().size(),
                dataset.getInstructors().size(),
                dataset.getProgramEnrollments().size(),
                dataset.getSectionEnrollments().size());
    }
}
