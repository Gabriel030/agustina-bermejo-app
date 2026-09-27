package com.red_escuela.backend.announcement.controller;

import com.red_escuela.backend.announcement.dto.AnnouncementResponse;
import com.red_escuela.backend.announcement.dto.CreateAnnouncementRequest;
import com.red_escuela.backend.announcement.dto.UpdateAnnouncementRequest;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.usecase.announcement.CreateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.announcement.CreateAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.DeleteAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.ListSectionAnnouncementsUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.UpdateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.announcement.UpdateAnnouncementUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('DOCENTE')")
public class AnnouncementController {

    private final CreateAnnouncementUseCase createAnnouncement;
    private final UpdateAnnouncementUseCase updateAnnouncement;
    private final DeleteAnnouncementUseCase deleteAnnouncement;
    private final ListSectionAnnouncementsUseCase listSectionAnnouncements;

    public AnnouncementController(CreateAnnouncementUseCase createAnnouncement,
                                  UpdateAnnouncementUseCase updateAnnouncement,
                                  DeleteAnnouncementUseCase deleteAnnouncement,
                                  ListSectionAnnouncementsUseCase listSectionAnnouncements) {
        this.createAnnouncement = createAnnouncement;
        this.updateAnnouncement = updateAnnouncement;
        this.deleteAnnouncement = deleteAnnouncement;
        this.listSectionAnnouncements = listSectionAnnouncements;
    }

    @PostMapping("/subject-sections/{sectionId}/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse createForSubjectSection(@PathVariable Long sectionId,
                                                        @Valid @RequestBody CreateAnnouncementRequest request) {
        return AnnouncementResponse.from(createAnnouncement.create(toCommand(SectionType.PROGRAM, sectionId, request)));
    }

    @PostMapping("/course-sections/{sectionId}/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse createForCourseSection(@PathVariable Long sectionId,
                                                       @Valid @RequestBody CreateAnnouncementRequest request) {
        return AnnouncementResponse.from(createAnnouncement.create(toCommand(SectionType.COURSE, sectionId, request)));
    }

    @GetMapping("/subject-sections/{sectionId}/announcements")
    public List<AnnouncementResponse> listForSubjectSection(@PathVariable Long sectionId) {
        return listSectionAnnouncements.list(SectionType.PROGRAM, sectionId).stream()
                .map(AnnouncementResponse::from).toList();
    }

    @GetMapping("/course-sections/{sectionId}/announcements")
    public List<AnnouncementResponse> listForCourseSection(@PathVariable Long sectionId) {
        return listSectionAnnouncements.list(SectionType.COURSE, sectionId).stream()
                .map(AnnouncementResponse::from).toList();
    }

    @PutMapping("/announcements/{id}")
    public AnnouncementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return AnnouncementResponse.from(updateAnnouncement.update(new UpdateAnnouncementCommand(
                id, request.title(), request.body(), request.category(), request.urgent(), request.expiresAt())));
    }

    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteAnnouncement.delete(id);
    }

    private CreateAnnouncementCommand toCommand(SectionType type, Long sectionId, CreateAnnouncementRequest request) {
        return new CreateAnnouncementCommand(type, sectionId, request.title(), request.body(),
                request.category(), request.urgent(), request.expiresAt());
    }
}
