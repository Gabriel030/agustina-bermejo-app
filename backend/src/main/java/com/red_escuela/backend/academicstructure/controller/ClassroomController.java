package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.ClassroomAvailabilityResponse;
import com.red_escuela.backend.academicstructure.dto.ClassroomResponse;
import com.red_escuela.backend.academicstructure.dto.CreateClassroomRequest;
import com.red_escuela.backend.academicstructure.usecase.classroom.CreateClassroomUseCase;
import com.red_escuela.backend.academicstructure.usecase.classroom.DeactivateClassroomUseCase;
import com.red_escuela.backend.academicstructure.usecase.classroom.GetClassroomAvailabilityUseCase;
import com.red_escuela.backend.academicstructure.usecase.classroom.ListClassroomsUseCase;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@RestController
public class ClassroomController {

    private final CreateClassroomUseCase createClassroom;
    private final ListClassroomsUseCase listClassrooms;
    private final DeactivateClassroomUseCase deactivateClassroom;
    private final GetClassroomAvailabilityUseCase getAvailability;

    public ClassroomController(
            CreateClassroomUseCase createClassroom,
            ListClassroomsUseCase listClassrooms,
            DeactivateClassroomUseCase deactivateClassroom,
            GetClassroomAvailabilityUseCase getAvailability
    ) {
        this.createClassroom = createClassroom;
        this.listClassrooms = listClassrooms;
        this.deactivateClassroom = deactivateClassroom;
        this.getAvailability = getAvailability;
    }

    @PostMapping("/classrooms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ClassroomResponse create(@Valid @RequestBody CreateClassroomRequest request) {
        return createClassroom.create(request);
    }

    @GetMapping("/classrooms")
    @PreAuthorize("isAuthenticated()")
    public List<ClassroomResponse> list() {
        return listClassrooms.list();
    }

    @PostMapping("/classrooms/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        deactivateClassroom.deactivate(id);
    }

    @GetMapping("/terms/{id}/classrooms/availability")
    @PreAuthorize("isAuthenticated()")
    public List<ClassroomAvailabilityResponse> availability(
            @PathVariable Long id,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime
    ) {
        return getAvailability.getAvailability(id, dayOfWeek, startTime, endTime);
    }
}
