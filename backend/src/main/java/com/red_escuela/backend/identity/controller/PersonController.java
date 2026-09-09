package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.dto.NotificationPreferencesResponse;
import com.red_escuela.backend.identity.dto.PersonResponse;
import com.red_escuela.backend.identity.dto.SessionResponse;
import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;
import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.usecase.person.ChangeOwnPasswordUseCase;
import com.red_escuela.backend.identity.usecase.person.GetActiveSessionsUseCase;
import com.red_escuela.backend.identity.usecase.person.GetNotificationPreferencesUseCase;
import com.red_escuela.backend.identity.usecase.person.GetOwnProfileUseCase;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateNotificationPreferencesUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateOwnProfileUseCase;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
public class PersonController {

    private final GetOwnProfileUseCase getOwnProfileUseCase;
    private final UpdateOwnProfileUseCase updateOwnProfileUseCase;
    private final ChangeOwnPasswordUseCase changeOwnPasswordUseCase;
    private final GetActiveSessionsUseCase getActiveSessionsUseCase;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;
    private final GetNotificationPreferencesUseCase getNotificationPreferencesUseCase;
    private final UpdateNotificationPreferencesUseCase updateNotificationPreferencesUseCase;
    private final CurrentPersonService currentPersonService;

    public PersonController(GetOwnProfileUseCase getOwnProfileUseCase,
                            UpdateOwnProfileUseCase updateOwnProfileUseCase,
                            ChangeOwnPasswordUseCase changeOwnPasswordUseCase,
                            GetActiveSessionsUseCase getActiveSessionsUseCase,
                            RevokeAllSessionsUseCase revokeAllSessionsUseCase,
                            GetNotificationPreferencesUseCase getNotificationPreferencesUseCase,
                            UpdateNotificationPreferencesUseCase updateNotificationPreferencesUseCase,
                            CurrentPersonService currentPersonService) {
        this.getOwnProfileUseCase = getOwnProfileUseCase;
        this.updateOwnProfileUseCase = updateOwnProfileUseCase;
        this.changeOwnPasswordUseCase = changeOwnPasswordUseCase;
        this.getActiveSessionsUseCase = getActiveSessionsUseCase;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
        this.getNotificationPreferencesUseCase = getNotificationPreferencesUseCase;
        this.updateNotificationPreferencesUseCase = updateNotificationPreferencesUseCase;
        this.currentPersonService = currentPersonService;
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
}
