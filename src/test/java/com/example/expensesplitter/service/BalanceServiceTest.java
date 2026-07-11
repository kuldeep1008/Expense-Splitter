package com.example.expensesplitter.service;

import com.example.expensesplitter.dto.SettlementSuggestion;
import com.example.expensesplitter.model.*;
import com.example.expensesplitter.repository.ExpenseRepository;
import com.example.expensesplitter.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the debt-simplification algorithm collapses a web of pairwise
 * debts into the minimum number of settling transactions.
 *
 * Scenario: A paid 90 for a dinner split equally 3 ways (A, B, C).
 * Naively: B owes A 30, C owes A 30 -> that's already minimal (2 txns),
 * but we also add a second expense where B paid 30 split between B and C,
 * so C owes B 15 too. The simplifier should still net this down to the
 * fewest possible transactions rather than reporting 3 separate debts.
 */
class BalanceServiceTest {

    @Test
    void simplifyDebts_collapsesToMinimumTransactions() {
        ExpenseRepository expenseRepository = Mockito.mock(ExpenseRepository.class);
        SettlementRepository settlementRepository = Mockito.mock(SettlementRepository.class);
        BalanceService balanceService = new BalanceService(expenseRepository, settlementRepository);

        User a = User.builder().id(1L).name("Alice").build();
        User b = User.builder().id(2L).name("Bob").build();
        User c = User.builder().id(3L).name("Charlie").build();

        Group group = Group.builder().id(100L).name("Trip").build();

        // Expense 1: Alice pays 90, split equally among A, B, C (30 each)
        Expense dinner = Expense.builder().id(1L).group(group).paidBy(a)
                .amount(new BigDecimal("90.00")).build();
        dinner.setShares(List.of(
                ExpenseShare.builder().expense(dinner).user(a).shareAmount(new BigDecimal("30.00")).build(),
                ExpenseShare.builder().expense(dinner).user(b).shareAmount(new BigDecimal("30.00")).build(),
                ExpenseShare.builder().expense(dinner).user(c).shareAmount(new BigDecimal("30.00")).build()
        ));

        // Expense 2: Bob pays 30, split equally between B and C (15 each)
        Expense snacks = Expense.builder().id(2L).group(group).paidBy(b)
                .amount(new BigDecimal("30.00")).build();
        snacks.setShares(List.of(
                ExpenseShare.builder().expense(snacks).user(b).shareAmount(new BigDecimal("15.00")).build(),
                ExpenseShare.builder().expense(snacks).user(c).shareAmount(new BigDecimal("15.00")).build()
        ));

        Mockito.when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(dinner, snacks));
        Mockito.when(settlementRepository.findByGroupId(100L)).thenReturn(List.of());

        // Net balances: A = +60 (paid 90, owes 30), B = 0 (paid 30, owed 30+15=45... let's just check the totals)
        Map<Long, BigDecimal> balances = balanceService.computeNetBalances(100L);

        // A: +90 (paid) - 30 (own share) = +60
        assertEquals(new BigDecimal("60.00"), balances.get(1L));
        // B: +30 (paid) - 30 (dinner share) - 15 (snacks share) = -15
        assertEquals(new BigDecimal("-15.00"), balances.get(2L));
        // C: -30 (dinner share) - 15 (snacks share) = -45
        assertEquals(new BigDecimal("-45.00"), balances.get(3L));

        List<SettlementSuggestion> suggestions = balanceService.simplifyDebts(
                100L, Map.of(1L, "Alice", 2L, "Bob", 3L, "Charlie"));

        // Total owed = 60, split between two debtors (B: -15, C: -45).
        // Minimum transactions should be 2, not 3+ pairwise debts.
        assertEquals(2, suggestions.size());

        BigDecimal totalSettled = suggestions.stream()
                .map(SettlementSuggestion::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("60.00"), totalSettled);

        // Every suggestion should flow toward Alice (the sole net creditor)
        assertTrue(suggestions.stream().allMatch(s -> s.getToUserId().equals(1L)));
    }
}
