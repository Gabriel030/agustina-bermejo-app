package com.red_escuela.backend.notification.broadcast.template.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;
import com.red_escuela.backend.notification.broadcast.template.usecase.CreateWhatsAppTemplateMappingUseCase;
import com.red_escuela.backend.notification.broadcast.template.usecase.ListWhatsAppTemplateMappingsUseCase;
import com.red_escuela.backend.notification.broadcast.template.usecase.UpdateWhatsAppTemplateMappingUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/institute-settings/notifications/whatsapp/templates")
public class WhatsAppTemplateMappingController {

    private final ListWhatsAppTemplateMappingsUseCase listMappings;
    private final CreateWhatsAppTemplateMappingUseCase createMapping;
    private final UpdateWhatsAppTemplateMappingUseCase updateMapping;

    public WhatsAppTemplateMappingController(ListWhatsAppTemplateMappingsUseCase listMappings,
                                             CreateWhatsAppTemplateMappingUseCase createMapping,
                                             UpdateWhatsAppTemplateMappingUseCase updateMapping) {
        this.listMappings = listMappings;
        this.createMapping = createMapping;
        this.updateMapping = updateMapping;
    }

    @RequiresPermission(code = "notification.read_whatsapp_templates", legacyRoles = "ROLE_DIRECTOR")
    @GetMapping
    public List<WhatsAppTemplateMappingResponse> listMappings() {
        return listMappings.listMappings();
    }

    @RequiresPermission(code = "notification.create_whatsapp_template", legacyRoles = "ROLE_DIRECTOR")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WhatsAppTemplateMappingResponse createMapping(@Valid @RequestBody WhatsAppTemplateMappingRequest request) {
        return createMapping.createMapping(request);
    }

    @RequiresPermission(code = "notification.update_whatsapp_template", legacyRoles = "ROLE_DIRECTOR")
    @PutMapping("/{id}")
    public WhatsAppTemplateMappingResponse updateMapping(@PathVariable Long id, @Valid @RequestBody WhatsAppTemplateMappingRequest request) {
        return updateMapping.updateMapping(id, request);
    }
}
