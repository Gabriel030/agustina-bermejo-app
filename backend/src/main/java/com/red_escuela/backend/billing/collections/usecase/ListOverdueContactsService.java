package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.domain.OverdueContact;
import com.red_escuela.backend.billing.collections.dto.OverdueContactPageResponse;
import com.red_escuela.backend.billing.collections.dto.OverdueContactResponse;
import com.red_escuela.backend.billing.collections.repository.OverdueContactQuery;
import com.red_escuela.backend.billing.collections.repository.OverdueContactQuery.LastReminderRow;
import com.red_escuela.backend.billing.collections.repository.OverdueContactQuery.StudentIdentityRow;
import com.red_escuela.backend.billing.collections.service.OverdueContactSegment;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@UseCase
public class ListOverdueContactsService implements ListOverdueContactsUseCase {
    public static final int MAX_PAGE_SIZE = 100;

    private final OverdueContactSegment segment;
    private final OverdueContactQuery overdueContactQuery;

    public ListOverdueContactsService(OverdueContactSegment segment, OverdueContactQuery overdueContactQuery) {
        this.segment = segment;
        this.overdueContactQuery = overdueContactQuery;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public OverdueContactPageResponse listOverdueContacts(int page, int size, Currency currency) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        Page<OverdueContact> contacts = segment.findContacts(ApplicationTime.today(), currency, PageRequest.of(boundedPage, boundedSize));
        List<Long> studentIds = contacts.getContent().stream().map(OverdueContact::studentId).toList();
        Map<Long, StudentIdentityRow> identities = overdueContactQuery.findIdentitiesOf(studentIds).stream()
                .collect(Collectors.toMap(StudentIdentityRow::studentId, Function.identity()));
        Map<Long, LocalDate> lastReminders = overdueContactQuery.findLastReminderDatesOf(studentIds).stream()
                .collect(Collectors.toMap(LastReminderRow::studentId, LastReminderRow::lastReminderDate));
        List<OverdueContactResponse> items = contacts.getContent().stream()
                .map(contact -> toResponse(contact, identities.get(contact.studentId()), lastReminders.get(contact.studentId()))).toList();
        return new OverdueContactPageResponse(items, boundedPage, boundedSize, contacts.getTotalElements());
    }

    private OverdueContactResponse toResponse(OverdueContact contact, StudentIdentityRow identity, LocalDate lastReminderDate) {
        return new OverdueContactResponse(contact.studentId(), identity.lastName() + ", " + identity.name(), identity.dni(), contact.oldestDaysOverdue(),
                contact.overdueInstallments(), contact.overdueAmounts(), lastReminderDate);
    }
}
