package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.StudentSegment;
import com.red_escuela.backend.billing.api.StudentSegmentMember;
import com.red_escuela.backend.billing.api.StudentSegmentParameter;
import com.red_escuela.backend.billing.collections.domain.ListedOverdueContact;
import com.red_escuela.backend.billing.collections.domain.OverdueAccountBalance;
import com.red_escuela.backend.billing.collections.domain.OverdueContact;
import com.red_escuela.backend.billing.collections.domain.OverdueContactCriteria;
import com.red_escuela.backend.billing.collections.domain.OverdueContactFilter;
import com.red_escuela.backend.billing.collections.domain.OverdueContactSelection;
import com.red_escuela.backend.billing.collections.repository.OverdueContactQuery;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class OverdueContactSegment implements StudentSegment {
    public static final String NAME = "OVERDUE_CONTACT";
    public static final String DAYS_OVERDUE_PARAMETER = "contactListDaysOverdue";
    public static final String OVERDUE_INSTALLMENTS_PARAMETER = "contactListOverdueInstallments";

    private final OverdueContactQuery overdueContactQuery;
    private final BillingConfigurationReader configurationReader;

    public OverdueContactSegment(OverdueContactQuery overdueContactQuery, BillingConfigurationReader configurationReader) {
        this.overdueContactQuery = overdueContactQuery;
        this.configurationReader = configurationReader;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<StudentSegmentParameter> parameters(LocalDate asOf) {
        OverdueContactCriteria criteria = criteriaEffectiveOn(asOf);
        return List.of(new StudentSegmentParameter(DAYS_OVERDUE_PARAMETER, Integer.toString(criteria.daysOverdue())),
                new StudentSegmentParameter(OVERDUE_INSTALLMENTS_PARAMETER, Integer.toString(criteria.overdueInstallments())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StudentSegmentMember> findMembers(LocalDate asOf, Pageable pageable) {
        Page<OverdueContact> contacts = findContacts(asOf, null, pageable);
        return new PageImpl<>(contacts.getContent().stream().map(contact -> new StudentSegmentMember(contact.studentId())).toList(), pageable,
                contacts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<OverdueContact> findContacts(LocalDate asOf, Currency currency, Pageable pageable) {
        List<OverdueContact> contacts = contactsOn(asOf, criteriaEffectiveOn(asOf)).stream()
                .filter(contact -> currency == null || contact.owesIn(currency)).toList();
        int from = (int) Math.min(pageable.getOffset(), contacts.size());
        int to = Math.min(from + pageable.getPageSize(), contacts.size());
        return new PageImpl<>(contacts.subList(from, to), pageable, contacts.size());
    }

    @Transactional(readOnly = true)
    public Page<ListedOverdueContact> findListedContacts(LocalDate asOf, OverdueContactFilter filter, Pageable pageable) {
        OverdueContactCriteria criteria = criteriaEffectiveOn(asOf);
        List<OverdueContact> today = contactsOn(asOf, criteria);
        Set<Long> newcomers = OverdueContactSelection.studentsNewlyIn(today, contactsOn(asOf.minusDays(1), criteria));
        List<ListedOverdueContact> listed = today.stream()
                .map(contact -> new ListedOverdueContact(contact, newcomers.contains(contact.studentId()),
                        criteria.reasonFor(contact.oldestDaysOverdue(), contact.overdueInstallments()).orElseThrow()))
                .filter(filter::accepts).toList();
        int from = (int) Math.min(pageable.getOffset(), listed.size());
        int to = Math.min(from + pageable.getPageSize(), listed.size());
        return new PageImpl<>(listed.subList(from, to), pageable, listed.size());
    }

    @Transactional(readOnly = true)
    public ContactListCounts countContacts(LocalDate asOf) {
        OverdueContactCriteria criteria = criteriaEffectiveOn(asOf);
        List<OverdueContact> today = contactsOn(asOf, criteria);
        List<OverdueContact> yesterday = contactsOn(asOf.minusDays(1), criteria);
        return new ContactListCounts(today.size(), OverdueContactSelection.studentsNewlyIn(today, yesterday).size());
    }

    private List<OverdueContact> contactsOn(LocalDate date, OverdueContactCriteria criteria) {
        List<OverdueAccountBalance> balances = overdueContactQuery.findOverdueAccountsAsOf(date).stream()
                .map(row -> new OverdueAccountBalance(row.studentId(), row.overdueInstallments(), row.oldestDaysOverdue(),
                        Money.of(row.overdueAmount(), Currency.valueOf(row.currency()))))
                .toList();
        return OverdueContactSelection.membersOf(criteria, balances);
    }

    private OverdueContactCriteria criteriaEffectiveOn(LocalDate date) {
        CollectionReminderSettings settings = configurationReader.collectionReminderSettingsEffectiveOn(date);
        return new OverdueContactCriteria(settings.contactListDaysOverdue(), settings.contactListOverdueInstallments());
    }

    public record ContactListCounts(int total, int newlyIn) {
    }
}
