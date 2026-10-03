package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.dto.NotificationPreferencesResponse;
import com.red_escuela.backend.identity.dto.PersonResponse;
import com.red_escuela.backend.identity.dto.SessionResponse;
import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;
import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.usecase.person.ChangeOwnPasswordUseCase;
import com.red_escuela.backend.identity.usecase.person.GetActiveSessionsUseCase;
import com.red_escuela.backend.identity.usecase.person.GetNotificationPreferencesUseCase;
import com.red_escuela.backend.identity.usecase.person.GetOwnProfileUseCase;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.identity.usecase.person.RevokeSessionByIdUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateNotificationPreferencesUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateOwnProfileUseCase;
import com.red_escuela.backend.identity.usecase.person.UploadAvatarUseCase;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/me")
public class PersonController {

    private final GetOwnProfileUseCase getOwnProfileUseCase;
    private final UpdateOwnProfileUseCase updateOwnProfileUseCase;
    private final ChangeOwnPasswordUseCase changeOwnPasswordUseCase;
    private final GetActiveSessionsUseCase getActiveSessionsUseCase;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;
    private final RevokeSessionByIdUseCase revokeSessionByIdUseCase;
    private final GetNotificationPreferencesUseCase getNotificationPreferencesUseCase;
    private final UpdateNotificationPreferencesUseCase updateNotificationPreferencesUseCase;
    private final UploadAvatarUseCase uploadAvatarUseCase;
    private final CurrentPersonService currentPersonService;
    private final FileStorageService fileStorageService;

    public PersonController(GetOwnProfileUseCase getOwnProfileUseCase,
                            UpdateOwnProfileUseCase updateOwnProfileUseCase,
                            ChangeOwnPasswordUseCase changeOwnPasswordUseCase,
                            GetActiveSessionsUseCase getActiveSessionsUseCase,
                            RevokeAllSessionsUseCase revokeAllSessionsUseCase,
                            RevokeSessionByIdUseCase revokeSessionByIdUseCase,
                            GetNotificationPreferencesUseCase getNotificationPreferencesUseCase,
                            UpdateNotificationPreferencesUseCase updateNotificationPreferencesUseCase,
                            UploadAvatarUseCase uploadAvatarUseCase,
                            CurrentPersonService currentPersonService,
                            FileStorageService fileStorageService) {
        this.getOwnProfileUseCase = getOwnProfileUseCase;
        this.updateOwnProfileUseCase = updateOwnProfileUseCase;
        this.changeOwnPasswordUseCase = changeOwnPasswordUseCase;
        this.getActiveSessionsUseCase = getActiveSessionsUseCase;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
        this.revokeSessionByIdUseCase = revokeSessionByIdUseCase;
        this.getNotificationPreferencesUseCase = getNotificationPreferencesUseCase;
        this.updateNotificationPreferencesUseCase = updateNotificationPreferencesUseCase;
        this.uploadAvatarUseCase = uploadAvatarUseCase;
        this.currentPersonService = currentPersonService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public PersonResponse getOwnProfile() {
        return PersonResponse.from(getOwnProfileUseCase.getOwnProfile());
    }

    @PutMapping
    public PersonResponse updateOwnProfile(@Valid @RequestBody UpdateOwnProfileRequest request) {
        return PersonResponse.from(updateOwnProfileUseCase.updateOwnProfile(request));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        changeOwnPasswordUseCase.changePassword(request);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> getActiveSessions() {
        return getActiveSessionsUseCase.getActiveSessions()
                .stream()
                .map(SessionResponse::from)
                .toList();
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions() {
        revokeAllSessionsUseCase.revokeAll(currentPersonService.resolveCurrentPerson());
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable Long id) {
        revokeSessionByIdUseCase.revokeById(id, currentPersonService.resolveCurrentPerson());
    }

    @GetMapping("/notification-preferences")
    public NotificationPreferencesResponse getNotificationPreferences() {
        return getNotificationPreferencesUseCase.getPreferences();
    }

    @PutMapping("/notification-preferences")
    public NotificationPreferencesResponse updateNotificationPreferences(
            @RequestBody UpdateNotificationPreferencesRequest request) {
        updateNotificationPreferencesUseCase.updatePreferences(request);
        return getNotificationPreferencesUseCase.getPreferences();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadAvatar(@RequestParam("file") MultipartFile file) {
        uploadAvatarUseCase.upload(file);
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar() {
        Person person = currentPersonService.resolveCurrentPerson();
        if (person.getAvatarPath() == null || !fileStorageService.exists(person.getAvatarPath())) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = fileStorageService.readBytes(person.getAvatarPath());
        String contentType = person.getAvatarPath().endsWith(".png") ? "image/png" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
