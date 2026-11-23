package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveReportDownload;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotFoundException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Component
@Transactional(readOnly = true)
public class InvitationWaveReportWriter {

    private static final String BOM = "\uFEFF";
    private static final String HEADER = "personId,status,exclusionReason,lastErrorCode,attempts,sentAt";
    private static final int PAGE_SIZE = 1000;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;

    public InvitationWaveReportWriter(InvitationWaveRepository waveRepository,
                                      InvitationWaveRecipientRepository recipientRepository) {
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
    }

    public InvitationWaveReportDownload writeInvitationWaveReport(Long waveId) {
        if (!waveRepository.existsById(waveId)) {
            throw new InvitationWaveNotFoundException(waveId);
        }
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append("\r\n");
        int page = 0;
        Page<InvitationWaveRecipient> recipients;
        do {
            recipients = recipientRepository.findByWaveId(waveId, PageRequest.of(page++, PAGE_SIZE, Sort.by("id")));
            recipients.forEach(recipient -> csv.append(line(recipient)));
        } while (recipients.hasNext());
        String filename = "invitaciones-oleada-" + waveId + "-" + ApplicationTime.today().format(FILE_DATE) + ".csv";
        return new InvitationWaveReportDownload(filename, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String line(InvitationWaveRecipient recipient) {
        return recipient.getPersonId() + "," + recipient.getStatus().name() + ","
                + (recipient.getExclusionReason() == null ? "" : recipient.getExclusionReason().name()) + ","
                + (recipient.getLastErrorCode() == null ? "" : recipient.getLastErrorCode().name()) + ","
                + recipient.getAttempts() + ","
                + (recipient.getSentAt() == null ? "" : recipient.getSentAt().toString()) + "\r\n";
    }
}
