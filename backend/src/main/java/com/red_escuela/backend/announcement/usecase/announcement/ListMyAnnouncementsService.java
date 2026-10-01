package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@UseCase
public class ListMyAnnouncementsService implements ListMyAnnouncementsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final StudentRepository studentRepository;
    private final CurrentPersonService currentPersonService;

    public ListMyAnnouncementsService(AnnouncementRepository announcementRepository,
                                      StudentRepository studentRepository,
                                      CurrentPersonService currentPersonService) {
        this.announcementRepository = announcementRepository;
        this.studentRepository = studentRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> listForCurrentStudent() {
        Person person = currentPersonService.resolveCurrentPerson();
        Optional<Student> student = studentRepository.findByPerson_Id(person.getId());
        return student
                .map(s -> announcementRepository.findFeedForStudent(s.getId(), LocalDate.now()))
                .orElseGet(List::of);
    }
}
