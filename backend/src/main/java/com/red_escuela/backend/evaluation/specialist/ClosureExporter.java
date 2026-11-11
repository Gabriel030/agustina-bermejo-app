package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.evaluation.domain.ClosureExportFormat;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosureExportFile;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.exception.ClosureNotClosedException;
import com.red_escuela.backend.evaluation.exception.ClosureNotFoundException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import org.springframework.stereotype.Service;

@Service
public class ClosureExporter {

    private final CommissionClosureRepository closureRepository;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final CommissionClosureSheetReader sheetReader;
    private final ClosureWorkbookWriter workbookWriter;
    private final ClosureActaPdfWriter actaPdfWriter;
    private final ClosureExportFileNamer fileNamer;

    public ClosureExporter(CommissionClosureRepository closureRepository, SectionTeachingFinder sectionTeachingFinder,
            CommissionClosureSheetReader sheetReader, ClosureWorkbookWriter workbookWriter,
            ClosureActaPdfWriter actaPdfWriter, ClosureExportFileNamer fileNamer) {
        this.closureRepository = closureRepository;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sheetReader = sheetReader;
        this.workbookWriter = workbookWriter;
        this.actaPdfWriter = actaPdfWriter;
        this.fileNamer = fileNamer;
    }

    public ClosureExportFile export(Long closureId, ClosureExportFormat format, ClosureViewerProfile viewer, Long instructorId) {
        CommissionClosure closure = closureRepository.findById(closureId)
                .orElseThrow(() -> new ClosureNotFoundException(closureId));
        if (viewer == ClosureViewerProfile.TEACHER) {
            sectionTeachingFinder.requireTeaches(closure.getSectionKind(), closure.getSectionId(), instructorId);
        }
        if (format == ClosureExportFormat.PDF && closure.getStatus() != ClosureStatus.CLOSED) {
            throw new ClosureNotClosedException(closureId);
        }
        ClosureSheetResponse sheet = sheetReader.readSheet(ClosureSectionKind.of(closure.getSectionKind()),
                closure.getSectionId(), viewer, instructorId);
        byte[] content = format == ClosureExportFormat.XLSX
                ? workbookWriter.write(sheet, viewer)
                : actaPdfWriter.write(sheet);
        return new ClosureExportFile(content, fileNamer.nameOf(sheet, format), format.contentType());
    }
}
