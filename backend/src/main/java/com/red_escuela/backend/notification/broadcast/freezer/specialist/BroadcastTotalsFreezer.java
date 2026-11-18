package com.red_escuela.backend.notification.broadcast.freezer.specialist;

import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastTotals;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import org.springframework.stereotype.Component;

@Component
public class BroadcastTotalsFreezer {

    private final BroadcastRecipientRepository recipientRepository;

    public BroadcastTotalsFreezer(BroadcastRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    public void freezeTotals(Broadcast broadcast) {
        broadcast.freezeTotals(BroadcastTotals.from(recipientRepository.countByStatus(broadcast.getId()),
                recipientRepository.countExcludedByReason(broadcast.getId())));
    }
}
