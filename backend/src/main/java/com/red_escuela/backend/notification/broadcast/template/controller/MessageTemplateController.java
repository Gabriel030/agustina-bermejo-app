package com.red_escuela.backend.notification.broadcast.template.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;
import com.red_escuela.backend.notification.broadcast.template.dto.RenderPreviewRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.RenderPreviewResponse;
import com.red_escuela.backend.notification.broadcast.template.usecase.CreateMessageTemplateUseCase;
import com.red_escuela.backend.notification.broadcast.template.usecase.DeactivateMessageTemplateUseCase;
import com.red_escuela.backend.notification.broadcast.template.usecase.ListMessageTemplatesUseCase;
import com.red_escuela.backend.notification.broadcast.template.usecase.RenderTemplatePreviewUseCase;
import com.red_escuela.backend.notification.broadcast.template.usecase.UpdateMessageTemplateCommand;
import com.red_escuela.backend.notification.broadcast.template.usecase.UpdateMessageTemplateUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/message-templates")
public class MessageTemplateController {

    private final ListMessageTemplatesUseCase listMessageTemplates;
    private final CreateMessageTemplateUseCase createMessageTemplate;
    private final UpdateMessageTemplateUseCase updateMessageTemplate;
    private final DeactivateMessageTemplateUseCase deactivateMessageTemplate;
    private final RenderTemplatePreviewUseCase renderTemplatePreview;

    public MessageTemplateController(ListMessageTemplatesUseCase listMessageTemplates,
                                     CreateMessageTemplateUseCase createMessageTemplate,
                                     UpdateMessageTemplateUseCase updateMessageTemplate,
                                     DeactivateMessageTemplateUseCase deactivateMessageTemplate,
                                     RenderTemplatePreviewUseCase renderTemplatePreview) {
        this.listMessageTemplates = listMessageTemplates;
        this.createMessageTemplate = createMessageTemplate;
        this.updateMessageTemplate = updateMessageTemplate;
        this.deactivateMessageTemplate = deactivateMessageTemplate;
        this.renderTemplatePreview = renderTemplatePreview;
    }

    @RequiresPermission(code = "notification.read_message_templates", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping
    public List<MessageTemplateResponse> listMessageTemplates(@RequestParam(required = false) ChannelType channel,
                                                              @RequestParam(required = false) BroadcastCategory category) {
        return listMessageTemplates.listMessageTemplates(channel, category);
    }

    @RequiresPermission(code = "notification.create_message_template", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageTemplateResponse createMessageTemplate(@Valid @RequestBody MessageTemplateRequest request) {
        return createMessageTemplate.createMessageTemplate(request);
    }

    @RequiresPermission(code = "notification.update_message_template", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PutMapping("/{id}")
    public MessageTemplateResponse updateMessageTemplate(@PathVariable Long id,
                                                         @Valid @RequestBody MessageTemplateRequest request) {
        return updateMessageTemplate.updateMessageTemplate(new UpdateMessageTemplateCommand(id, request));
    }

    @RequiresPermission(code = "notification.deactivate_message_template", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateMessageTemplate(@PathVariable Long id) {
        deactivateMessageTemplate.deactivateMessageTemplate(id);
    }

    @RequiresPermission(code = "notification.read_message_templates", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PostMapping("/render-preview")
    public RenderPreviewResponse renderTemplatePreview(@Valid @RequestBody RenderPreviewRequest request) {
        return renderTemplatePreview.renderTemplatePreview(request);
    }
}
