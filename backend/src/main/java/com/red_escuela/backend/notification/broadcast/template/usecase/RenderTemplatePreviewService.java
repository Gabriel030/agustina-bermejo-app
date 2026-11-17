package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateRenderer;
import com.red_escuela.backend.notification.broadcast.template.dto.RenderPreviewRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.RenderPreviewResponse;
import com.red_escuela.backend.notification.broadcast.template.specialist.TemplateVariableSource;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RenderTemplatePreviewService implements RenderTemplatePreviewUseCase {

    private final TemplateVariableSource variableSource;

    public RenderTemplatePreviewService(TemplateVariableSource variableSource) {
        this.variableSource = variableSource;
    }

    @Override
    public RenderPreviewResponse renderTemplatePreview(RenderPreviewRequest request) {
        RenderedContent content = TemplateRenderer.render(request.channel(), request.subject(), request.bodyText(),
                variableSource.valuesFor(request.personId()));
        return new RenderPreviewResponse(content.subject(), content.body());
    }
}
