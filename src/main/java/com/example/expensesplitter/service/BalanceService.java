package com.example.expensesplitter.service;

import com.example.expensesplitter.dto.SettlementSuggestion;
import com.example.expensesplitter.model.Expense;
import com.example.expensesplitter.model.ExpenseShare;
import com.example.expensesplitter.model.Settlement;
import com.example.expensesplitter.repository.ExpenseRepository;
import com.example.expensesplitter.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Core "talking point" of the project.
 *
 * Naive expense splitting produces O(n^2) pairwise debts inside a group
 * (everyone can owe everyone). This service reduces that to the MINIMUM
 * number of transactions needed to settle all debts, using a greedy
 * max-creditor / max-debtor matching algorithm.
 *
 * How it works:
 *  1. Compute each user's net balance in the group:
 *     net = (total they paid for the group) - (total they owe across all expenses)
 *     - positive net  => group owes them money (creditor)
 *     - negative net  => they owe the group money (debtor)
 *  2. Subtract out any settlements that have already been recorded.
 *  3. Repeatedly match the biggest creditor with the biggest debtor and
 *     settle the smaller of the two amounts between them. Push the
 *     remainder back into the pool and repeat until everyone nets to zero.
 *
 * This greedy approach is proven to produce at most (n - 1) transactions
 * for n participants with non-zero balances, which is optimal in the
 * general case and is the same idea used by Splitwise's "simplify debts" feature.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public Map<Long, BigDecimal> computeNetBalances(Long groupId) {
        Map<Long, BigDecimal> net = new HashMap<>();

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        for (Expense expense : expenses) {
            Long payerId = expense.getPaidBy().getId();
            net.merge(payerId, expense.getAmount(), BigDecimal::add);

            for (ExpenseShare share : expense.getShares()) {
                Long owerId = share.getUser().getId();
                net.merge(owerId, share.getShareAmount().negate(), BigDecimal::add);
            }
        }

        // Apply recorded settlements: payer already paid payee, so it reduces
        // payer's debt (increases their net) and reduces payee's credit.
        List<Settlement> settlements = settlementRepository.findByGroupId(groupId);
        for (Settlement s : settlements) {
            net.merge(s.getPayer().getId(), s.getAmount(), BigDecimal::add);
            net.merge(s.getPayee().getId(), s.getAmount().negate(), BigDecimal::add);
        }

        return net;
    }

    /**
     * Returns the minimal list of "who should pay whom, how much" transactions
     * that settle every balance in the group.
     */
    public List<SettlementSuggestion> simplifyDebts(Long groupId, Map<Long, String> userNames) {
        Map<Long, BigDecimal> balances = computeNetBalances(groupId);

        // Max-heaps by absolute balance: creditors (positive) and debtors (negative)
        PriorityQueue<Map.Entry<Long, BigDecimal>> creditors =
                new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue()));
        PriorityQueue<Map.Entry<Long, BigDecimal>> debtors =
                new PriorityQueue<>((a, b) -> a.getValue().compareTo(b.getValue())); // most negative first

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().abs().compareTo(EPSILON) < 0) continue; // ~0, already settled
            if (entry.getValue().signum() > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            } else {
                debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }

        List<SettlementSuggestion> result = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<Long, BigDecimal> topCreditor = creditors.poll();
            Map.Entry<Long, BigDecimal> topDebtor = debtors.poll();

            BigDecimal owed = topDebtor.getValue().abs();
            BigDecimal settleAmount = owed.min(topCreditor.getValue());

            result.add(new SettlementSuggestion(
                    topDebtor.getKey(),
                    userNames.getOrDefault(topDebtor.getKey(), "User " + topDebtor.getKey()),
                    topCreditor.getKey(),
                    userNames.getOrDefault(topCreditor.getKey(), "User " + topCreditor.getKey()),
                    settleAmount
            ));

            BigDecimal remainingCredit = topCreditor.getValue().subtract(settleAmount);
            BigDecimal remainingDebt = owed.subtract(settleAmount).negate();

            if (remainingCredit.abs().compareTo(EPSILON) > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(topCreditor.getKey(), remainingCredit));
            }
            if (remainingDebt.abs().compareTo(EPSILON) > 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(topDebtor.getKey(), remainingDebt));
            }
        }

        return result;
    }
}
