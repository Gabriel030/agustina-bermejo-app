package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class OverdueContactSelection {

    private static final Comparator<OverdueContact> MOST_OVERDUE_FIRST = Comparator.comparingInt(OverdueContact::oldestDaysOverdue).reversed()
            .thenComparing(OverdueContact::studentId);

    private OverdueContactSelection() {
    }

    public static List<OverdueContact> membersOf(OverdueContactCriteria criteria, Collection<OverdueAccountBalance> balances) {
        Map<Long, List<OverdueAccountBalance>> balancesByStudent = new LinkedHashMap<>();
        for (OverdueAccountBalance balance : balances) {
            balancesByStudent.computeIfAbsent(balance.studentId(), id -> new ArrayList<>()).add(balance);
        }
        List<OverdueContact> members = new ArrayList<>();
        for (Map.Entry<Long, List<OverdueAccountBalance>> entry : balancesByStudent.entrySet()) {
            OverdueContact contact = contactOf(entry.getKey(), entry.getValue());
            if (criteria.isMetBy(contact.oldestDaysOverdue(), contact.overdueInstallments())) {
                members.add(contact);
            }
        }
        members.sort(MOST_OVERDUE_FIRST);
        return members;
    }

    public static Set<Long> studentsNewlyIn(Collection<OverdueContact> today, Collection<OverdueContact> yesterday) {
        Set<Long> newcomers = new HashSet<>();
        today.forEach(contact -> newcomers.add(contact.studentId()));
        yesterday.forEach(contact -> newcomers.remove(contact.studentId()));
        return newcomers;
    }

    private static OverdueContact contactOf(Long studentId, List<OverdueAccountBalance> balances) {
        int installments = 0;
        int oldest = 0;
        Map<Currency, Money> amountsByCurrency = new TreeMap<>();
        for (OverdueAccountBalance balance : balances) {
            installments += balance.overdueInstallments();
            oldest = Math.max(oldest, balance.oldestDaysOverdue());
            amountsByCurrency.merge(balance.overdueAmount().currency(), balance.overdueAmount(), Money::plus);
        }
        return new OverdueContact(studentId, oldest, installments, List.copyOf(amountsByCurrency.values()));
    }
}
