package com.red_escuela.backend.announcement.controller;

import com.red_escuela.backend.announcement.dto.AnnouncementResponse;
import com.red_escuela.backend.announcement.dto.AudiencePreviewResponse;
import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.dto.AudienceSegmentResponse;
import com.red_escuela.backend.announcement.dto.AudienceSpecRequest;
import com.red_escuela.backend.announcement.dto.CreateAnnouncementRequest;
import com.red_escuela.backend.announcement.dto.UpdateAnnouncementRequest;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.usecase.broadcast.AnnouncementReadCount;
import com.red_escuela.backend.announcement.usecase.broadcast.CountAnnouncementReadsUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.CreateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.broadcast.CreateAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.DeleteAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.ListAnnouncementReadReceiptsUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.ListAnnouncementRecipientsUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.ListSectionAnnouncementsUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.ListStaffSectionAnnouncementsUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.UpdateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.broadcast.UpdateAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.audience.ListAudienceSegmentsUseCase;
import com.red_escuela.backend.announcement.usecase.audience.PreviewAudienceCommand;
import com.red_escuela.backend.announcement.usecase.audience.PreviewAudienceUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('DOCENTE')")
public class AnnouncementController {

    private final CreateAnnouncementUseCase createAnnouncement;
    private final UpdateAnnouncementUseCase updateAnnouncement;
    private final DeleteAnnouncementUseCase deleteAnnouncement;
    private final ListSectionAnnouncementsUseCase listSectionAnnouncements;
    private final ListStaffSectionAnnouncementsUseCase listStaffSectionAnnouncements;
    private final ListAudienceSegmentsUseCase listAudienceSegments;
    private final PreviewAudienceUseCase previewAudience;
    private final ListAnnouncementRecipientsUseCase listAnnouncementRecipients;
    private final ListAnnouncementReadReceiptsUseCase listAnnouncementReadReceipts;
    private final CountAnnouncementReadsUseCase countAnnouncementReads;

    public AnnouncementController(CreateAnnouncementUseCase createAnnouncement,
                                  UpdateAnnouncementUseCase updateAnnouncement,
                                  DeleteAnnouncementUseCase deleteAnnouncement,
                                  ListSectionAnnouncementsUseCase listSectionAnnouncements,
                                  ListStaffSectionAnnouncementsUseCase listStaffSectionAnnouncements,
                                  ListAudienceSegmentsUseCase listAudienceSegments,
                                  PreviewAudienceUseCase previewAudience,
                                  ListAnnouncementRecipientsUseCase listAnnouncementRecipients,
                                  ListAnnouncementReadReceiptsUseCase listAnnouncementReadReceipts,
                                  CountAnnouncementReadsUseCase countAnnouncementReads) {
        this.createAnnouncement = createAnnouncement;
        this.updateAnnouncement = updateAnnouncement;
        this.deleteAnnouncement = deleteAnnouncement;
        this.listSectionAnnouncements = listSectionAnnouncements;
        this.listStaffSectionAnnouncements = listStaffSectionAnnouncements;
        this.listAudienceSegments = listAudienceSegments;
        this.previewAudience = previewAudience;
        this.listAnnouncementRecipients = listAnnouncementRecipients;
        this.listAnnouncementReadReceipts = listAnnouncementReadReceipts;
        this.countAnnouncementReads = countAnnouncementReads;
    }

    @PostMapping("/subject-sections/{sectionId}/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse createForSubjectSection(@PathVariable Long sectionId,
                                                        @Valid @RequestBody CreateAnnouncementRequest request) {
        return toResponse(createAnnouncement.create(toCommand(SectionType.PROGRAM, sectionId, request)));
    }

    @PostMapping("/course-sections/{sectionId}/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse createForCourseSection(@PathVariable Long sectionId,
                                                       @Valid @RequestBody CreateAnnouncementRequest request) {
        return toResponse(createAnnouncement.create(toCommand(SectionType.COURSE, sectionId, request)));
    }

    @GetMapping("/subject-sections/{sectionId}/announcements")
    public List<AnnouncementResponse> listForSubjectSection(@PathVariable Long sectionId) {
        return toResponses(listSectionAnnouncements.list(SectionType.PROGRAM, sectionId));
    }

    @GetMapping("/course-sections/{sectionId}/announcements")
    public List<AnnouncementResponse> listForCourseSection(@PathVariable Long sectionId) {
        return toResponses(listSectionAnnouncements.list(SectionType.COURSE, sectionId));
    }

    @GetMapping("/subject-sections/{sectionId}/announcements/read-receipts")
    @PreAuthorize("hasAnyRole('PRECEPTOR', 'DIRECTOR')")
    public List<AnnouncementResponse> listReadReceiptsForSubjectSection(@PathVariable Long sectionId) {
        return listStaffSectionAnnouncements.listForProgramSection(sectionId);
    }

    @GetMapping("/course-sections/{sectionId}/announcements/read-receipts")
    @PreAuthorize("hasAnyRole('PRECEPTOR', 'DIRECTOR')")
    public List<AnnouncementResponse> listReadReceiptsForCourseSection(@PathVariable Long sectionId) {
        return listStaffSectionAnnouncements.listForCourseSection(sectionId);
    }

    @GetMapping("/subject-sections/{sectionId}/audience-segments")
    public List<AudienceSegmentResponse> segmentsForSubjectSection(@PathVariable Long sectionId) {
        return listAudienceSegments.list(SectionType.PROGRAM, sectionId);
    }

    @GetMapping("/course-sections/{sectionId}/audience-segments")
    public List<AudienceSegmentResponse> segmentsForCourseSection(@PathVariable Long sectionId) {
        return listAudienceSegments.list(SectionType.COURSE, sectionId);
    }

    @PostMapping("/subject-sections/{sectionId}/audience-preview")
    public AudiencePreviewResponse previewForSubjectSection(@PathVariable Long sectionId,
                                                            @Valid @RequestBody AudienceSpecRequest request) {
        return previewAudience.preview(new PreviewAudienceCommand(SectionType.PROGRAM, sectionId, request.toSpec()));
    }

    @PostMapping("/course-sections/{sectionId}/audience-preview")
    public AudiencePreviewResponse previewForCourseSection(@PathVariable Long sectionId,
                                                           @Valid @RequestBody AudienceSpecRequest request) {
        return previewAudience.preview(new PreviewAudienceCommand(SectionType.COURSE, sectionId, request.toSpec()));
    }

    @PutMapping("/announcements/{id}")
    public AnnouncementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return toResponse(updateAnnouncement.update(new UpdateAnnouncementCommand(
                id, request.title(), request.body(), request.category(), request.urgent(), request.expiresAt(),
                request.scheduledFor(), request.audience() == null ? null : request.audience().toSpec(),
                Boolean.TRUE.equals(request.notifyRecipients()))));
    }

    @GetMapping("/announcements/{id}/recipients")
    @PreAuthorize("hasAnyRole('DOCENTE', 'PRECEPTOR', 'DIRECTOR')")
    public List<AnnouncementRecipientResponse> recipients(@PathVariable Long id) {
        return isInstructor() ? listAnnouncementRecipients.list(id) : listAnnouncementReadReceipts.list(id);
    }

    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteAnnouncement.delete(id);
    }

    private boolean isInstructor() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DOCENTE"));
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        return toResponses(List.of(announcement)).get(0);
    }

    private List<AnnouncementResponse> toResponses(List<Announcement> announcements) {
        Collection<Long> ids = announcements.stream().map(Announcement::getId).toList();
        Map<Long, AnnouncementReadCount> counts = countAnnouncementReads.countsOf(ids);
        return announcements.stream()
                .map(announcement -> {
                    AnnouncementReadCount count = counts.getOrDefault(announcement.getId(), AnnouncementReadCount.EMPTY);
                    return AnnouncementResponse.from(announcement, count.recipientCount(), count.readCount());
                })
                .toList();
    }

    private CreateAnnouncementCommand toCommand(SectionType type, Long sectionId, CreateAnnouncementRequest request) {
        return new CreateAnnouncementCommand(type, sectionId, request.title(), request.body(),
                request.category(), request.urgent(), request.expiresAt(), request.scheduledFor(),
                request.audience() == null ? null : request.audience().toSpec());
    }
}
