package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CancelProgramSectionClassSessionService implements CancelProgramSectionClassSessionUseCase {
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;

    public CancelProgramSectionClassSessionService(ClassSessionRepository classSessionRepository,
                                                    AttendanceSectionAccess sectionAccess,
                                                    ClassSessionUpserter classSessionUpserter) {
        this.classSessionRepository = classSessionRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    @Override
    @Transactional
    public void cancel(CancelProgramSectionClassSessionCommand command) {
        SubjectSection section = sectionAccess.findProgramSection(command.sectionId());
        sectionAccess.requireOwnProgramSection(command.sectionId(), command.instructorId());
        Instructor instructor = sectionAccess.resolveInstructor(command.instructorId());
        ClassSession session = classSessionUpserter.findOrCreateForProgramSection(section, command.classDate(), instructor);
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }
}
